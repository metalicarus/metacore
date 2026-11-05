package br.com.genosha.metacore.format.office;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Monta ZIPs byte a byte, com controle do que {@code java.util.zip.ZipOutputStream} não deixa
 * escolher: ordem, descritor de dados, flags e campos inválidos.
 */
final class ZipBuilder {

  private record Entry(String name, byte[] content, boolean deflate, int flags) {}

  private final List<Entry> entries = new ArrayList<>();
  private String comment = "";
  private boolean descriptors;
  private boolean zip64Marker;

  ZipBuilder add(String name, String content) {
    return add(name, content.getBytes(StandardCharsets.UTF_8));
  }

  ZipBuilder add(String name, byte[] content) {
    entries.add(new Entry(name, content, true, 0));
    return this;
  }

  ZipBuilder stored(String name, byte[] content) {
    entries.add(new Entry(name, content, false, 0));
    return this;
  }

  /** Entrada marcada como criptografada (o conteúdo continua legível, só a flag muda). */
  ZipBuilder encrypted(String name, String content) {
    entries.add(new Entry(name, content.getBytes(StandardCharsets.UTF_8), true, 1));
    return this;
  }

  ZipBuilder comment(String text) {
    this.comment = text;
    return this;
  }

  /** Grava os tamanhos num descritor depois dos dados, como o LibreOffice e o ZipOutputStream. */
  ZipBuilder descriptors() {
    this.descriptors = true;
    return this;
  }

  /** Marca o fim do diretório central como ZIP64. */
  ZipBuilder zip64Marker() {
    this.zip64Marker = true;
    return this;
  }

  byte[] build() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream central = new ByteArrayOutputStream();
    for (Entry entry : entries) {
      byte[] name = entry.name().getBytes(StandardCharsets.UTF_8);
      byte[] data = entry.deflate() ? deflate(entry.content()) : entry.content();
      CRC32 crc = new CRC32();
      crc.update(entry.content());
      int flags = entry.flags() | (1 << 11) | (descriptors ? 1 << 3 : 0);
      int method = entry.deflate() ? 8 : 0;
      long offset = out.size();
      boolean sizesInHeader = !descriptors;
      out.writeBytes(
          le(30)
              .putInt(0x04034b50)
              .putShort((short) 20)
              .putShort((short) flags)
              .putShort((short) method)
              .putShort((short) 0)
              .putShort((short) 0x5821)
              .putInt(sizesInHeader ? (int) crc.getValue() : 0)
              .putInt(sizesInHeader ? data.length : 0)
              .putInt(sizesInHeader ? entry.content().length : 0)
              .putShort((short) name.length)
              .putShort((short) 0)
              .array());
      out.writeBytes(name);
      out.writeBytes(data);
      if (descriptors) {
        out.writeBytes(
            le(16)
                .putInt(0x08074b50)
                .putInt((int) crc.getValue())
                .putInt(data.length)
                .putInt(entry.content().length)
                .array());
      }
      central.writeBytes(
          le(46)
              .putInt(0x02014b50)
              .putShort((short) 20)
              .putShort((short) 20)
              .putShort((short) flags)
              .putShort((short) method)
              .putShort((short) 0)
              .putShort((short) 0x5821)
              .putInt((int) crc.getValue())
              .putInt(data.length)
              .putInt(entry.content().length)
              .putShort((short) name.length)
              .putShort((short) 0)
              .putShort((short) 0)
              .putShort((short) 0)
              .putShort((short) 0)
              .putInt(0)
              .putInt((int) offset)
              .array());
      central.writeBytes(name);
    }
    int directoryOffset = out.size();
    out.writeBytes(central.toByteArray());
    byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
    out.writeBytes(
        le(22)
            .putInt(0x06054b50)
            .putShort((short) 0)
            .putShort((short) 0)
            .putShort((short) entries.size())
            .putShort(zip64Marker ? (short) 0xFFFF : (short) entries.size())
            .putInt(central.size())
            .putInt(directoryOffset)
            .putShort((short) commentBytes.length)
            .array());
    out.writeBytes(commentBytes);
    return out.toByteArray();
  }

  static byte[] deflate(byte[] content) {
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
    deflater.setInput(content);
    deflater.finish();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    while (!deflater.finished()) {
      out.write(buffer, 0, deflater.deflate(buffer));
    }
    deflater.end();
    return out.toByteArray();
  }

  private static ByteBuffer le(int size) {
    return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
  }
}
