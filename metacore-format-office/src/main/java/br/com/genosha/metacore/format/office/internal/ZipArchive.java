package br.com.genosha.metacore.format.office.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipException;

/**
 * Leitor do diretório central de um ZIP sobre um canal, sem depender de {@code java.util.zip
 * .ZipFile}, que exige um arquivo em disco. Só os métodos STORED e DEFLATED são lidos; ZIP64 não é
 * suportado.
 */
public final class ZipArchive {

  /** Uma entrada do diretório central. */
  public record Entry(
      String name, int method, int flags, long compressedSize, long size, long localOffset) {

    /** Indica se a entrada é criptografada. */
    public boolean encrypted() {
      return (flags & 1) != 0;
    }
  }

  /** O arquivo não tem um diretório central válido. */
  public static final class NotAnArchiveException extends ZipException {
    private static final long serialVersionUID = 1L;

    NotAnArchiveException(String message) {
      super(message);
    }
  }

  static final int LOCAL_HEADER = 0x04034b50;
  static final int CENTRAL_HEADER = 0x02014b50;
  static final int END_OF_CENTRAL_DIRECTORY = 0x06054b50;
  static final int STORED = 0;
  static final int DEFLATED = 8;
  static final int UTF8_NAMES = 1 << 11;

  private static final int EOCD_SIZE = 22;
  private static final int MAX_COMMENT = 0xFFFF;
  private static final long MAX_CENTRAL_DIRECTORY = 16L * 1024 * 1024;

  private final SeekableByteChannel channel;
  private final List<Entry> entries;
  private final String comment;

  private ZipArchive(SeekableByteChannel channel, List<Entry> entries, String comment) {
    this.channel = channel;
    this.entries = entries;
    this.comment = comment;
  }

  /**
   * Lê o diretório central.
   *
   * @throws NotAnArchiveException se o fim do diretório central não existe ou é inconsistente, como
   *     num arquivo truncado
   */
  public static ZipArchive read(SeekableByteChannel channel) throws IOException {
    long size = channel.size();
    int tailLength = (int) Math.min(size, EOCD_SIZE + MAX_COMMENT);
    ByteBuffer tail = readAt(channel, size - tailLength, tailLength);
    int eocd = -1;
    for (int i = tailLength - EOCD_SIZE; i >= 0; i--) {
      if (tail.getInt(i) == END_OF_CENTRAL_DIRECTORY
          && i + EOCD_SIZE + Short.toUnsignedInt(tail.getShort(i + 20)) <= tailLength) {
        eocd = i;
        break;
      }
    }
    if (eocd < 0) {
      throw new NotAnArchiveException("No end of central directory record");
    }
    int count = Short.toUnsignedInt(tail.getShort(eocd + 10));
    long directorySize = Integer.toUnsignedLong(tail.getInt(eocd + 12));
    long directoryOffset = Integer.toUnsignedLong(tail.getInt(eocd + 16));
    int commentLength = Short.toUnsignedInt(tail.getShort(eocd + 20));
    if (count == 0xFFFF || directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) {
      throw new ZipException("ZIP64 archives are not supported");
    }
    long eocdOffset = size - tailLength + eocd;
    if (directoryOffset + directorySize > eocdOffset) {
      throw new NotAnArchiveException("Central directory is outside the file");
    }
    if (directorySize > MAX_CENTRAL_DIRECTORY) {
      throw new ZipException("Central directory is larger than " + MAX_CENTRAL_DIRECTORY);
    }
    byte[] commentBytes = new byte[commentLength];
    tail.get(eocd + EOCD_SIZE, commentBytes);
    ByteBuffer directory = readAt(channel, directoryOffset, (int) directorySize);
    return new ZipArchive(
        channel,
        entries(directory, count, eocdOffset),
        new String(commentBytes, StandardCharsets.UTF_8).strip());
  }

  private static List<Entry> entries(ByteBuffer directory, int count, long limit)
      throws ZipException {
    List<Entry> entries = new ArrayList<>(Math.min(count, 4096));
    int position = 0;
    for (int i = 0; i < count; i++) {
      if (position + 46 > directory.limit() || directory.getInt(position) != CENTRAL_HEADER) {
        throw new NotAnArchiveException("Central directory entry " + i + " is invalid");
      }
      int flags = Short.toUnsignedInt(directory.getShort(position + 8));
      int method = Short.toUnsignedInt(directory.getShort(position + 10));
      long compressed = Integer.toUnsignedLong(directory.getInt(position + 20));
      long size = Integer.toUnsignedLong(directory.getInt(position + 24));
      int nameLength = Short.toUnsignedInt(directory.getShort(position + 28));
      int extraLength = Short.toUnsignedInt(directory.getShort(position + 30));
      int commentLength = Short.toUnsignedInt(directory.getShort(position + 32));
      long offset = Integer.toUnsignedLong(directory.getInt(position + 42));
      int next = position + 46 + nameLength + extraLength + commentLength;
      if (next > directory.limit()) {
        throw new NotAnArchiveException("Central directory entry " + i + " is truncated");
      }
      if (compressed == 0xFFFFFFFFL || size == 0xFFFFFFFFL || offset == 0xFFFFFFFFL) {
        throw new ZipException("ZIP64 archives are not supported");
      }
      if (offset + compressed > limit) {
        throw new NotAnArchiveException("Entry " + i + " points outside the file");
      }
      byte[] name = new byte[nameLength];
      directory.get(position + 46, name);
      entries.add(new Entry(name(name, flags), method, flags, compressed, size, offset));
      position = next;
    }
    return List.copyOf(entries);
  }

  /** Nome de entrada em UTF-8 quando o bit 11 diz isso; senão, Latin-1. */
  static String name(byte[] bytes, int flags) {
    return new String(
        bytes, (flags & UTF8_NAMES) != 0 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
  }

  public List<Entry> entries() {
    return entries;
  }

  public String comment() {
    return comment;
  }

  /** Entrada pelo nome, sem diferenciar maiúsculas, como nas partes de um pacote OPC. */
  public Optional<Entry> find(String name) {
    String wanted = name.toLowerCase(Locale.ROOT);
    return entries.stream()
        .filter(e -> e.name().toLowerCase(Locale.ROOT).equals(wanted))
        .findFirst();
  }

  /** Conteúdo de uma entrada, até {@code maxBytes}. */
  public Inflation read(Entry entry, int maxBytes) throws IOException {
    if (entry.encrypted()) {
      throw new ZipException("Entry '" + entry.name() + "' is encrypted");
    }
    ByteBuffer header = readAt(channel, entry.localOffset(), 30);
    if (header.limit() < 30 || header.getInt(0) != LOCAL_HEADER) {
      throw new ZipException("Local header of '" + entry.name() + "' is invalid");
    }
    long dataOffset =
        entry.localOffset()
            + 30
            + Short.toUnsignedInt(header.getShort(26))
            + Short.toUnsignedInt(header.getShort(28));
    if (dataOffset + entry.compressedSize() > channel.size()) {
      throw new ZipException("Data of '" + entry.name() + "' is outside the file");
    }
    channel.position(dataOffset);
    InputStream data =
        new BufferedInputStream(
            new BoundedInputStream(Channels.newInputStream(channel), entry.compressedSize()));
    return switch (entry.method()) {
      case STORED -> Inflation.stored(data, entry.compressedSize(), true, maxBytes);
      case DEFLATED ->
          Inflation.inflate(
              new PushbackInputStream(data, Inflation.BUFFER),
              entry.compressedSize(),
              true,
              maxBytes,
              Long.MAX_VALUE);
      default ->
          throw new ZipException(
              "Entry '" + entry.name() + "' uses unsupported compression method " + entry.method());
    };
  }

  private static ByteBuffer readAt(SeekableByteChannel channel, long position, int length)
      throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    channel.position(position);
    while (buffer.hasRemaining()) {
      if (channel.read(buffer) < 0) {
        break;
      }
    }
    return buffer.flip();
  }
}
