package br.com.genosha.metacore.format.office.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Predicate;

/**
 * Percorre um ZIP pelos cabeçalhos locais, do início ao fim, sem o diretório central. Serve ao
 * detector, que só vê o começo do arquivo, e a arquivos truncados, que perderam o diretório
 * central.
 */
public final class LocalScan {

  /** O que a varredura achou numa entrada. */
  public interface Visitor {
    /**
     * Recebe uma entrada.
     *
     * @param name nome da entrada
     * @param content o conteúdo, só para entradas pedidas; pode estar incompleto
     * @return {@code false} para encerrar a varredura
     */
    boolean visit(String name, Inflation content) throws IOException;
  }

  private static final int DATA_DESCRIPTOR = 0x08074b50;
  private static final int DESCRIPTOR_FLAG = 1 << 3;

  private LocalScan() {}

  /**
   * Varre as entradas.
   *
   * @param wanted entradas cujo conteúdo deve ser entregue
   * @param maxBytes quanto guardar de cada entrada pedida
   * @param maxScanned teto de bytes descompactados só para achar o fim de uma entrada
   * @return {@code true} se chegou ao fim das entradas, {@code false} se parou antes por dados
   *     truncados, inválidos ou além dos limites
   */
  public static boolean scan(
      InputStream source, Predicate<String> wanted, int maxBytes, long maxScanned, Visitor visitor)
      throws IOException {
    PushbackInputStream in = new PushbackInputStream(source, Inflation.BUFFER);
    while (true) {
      byte[] fixed = in.readNBytes(30);
      if (fixed.length < 4) {
        return fixed.length == 0;
      }
      ByteBuffer header = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN);
      if (header.getInt(0) != ZipArchive.LOCAL_HEADER) {
        // O diretório central (ou o fim) começa depois da última entrada.
        return header.getInt(0) == 0x02014b50 || header.getInt(0) == 0x06054b50;
      }
      if (fixed.length < 30) {
        return false;
      }
      int flags = Short.toUnsignedInt(header.getShort(6));
      int method = Short.toUnsignedInt(header.getShort(8));
      long compressed = Integer.toUnsignedLong(header.getInt(18));
      byte[] nameBytes = in.readNBytes(Short.toUnsignedInt(header.getShort(26)));
      if (nameBytes.length < Short.toUnsignedInt(header.getShort(26))
          || !Inflation.skip(in, Short.toUnsignedInt(header.getShort(28)))) {
        return false;
      }
      String name = ZipArchive.name(nameBytes, flags);
      boolean descriptor = (flags & DESCRIPTOR_FLAG) != 0;
      boolean keep = wanted.test(name) && (flags & 1) == 0;
      Inflation content;
      if (method == ZipArchive.DEFLATED && descriptor) {
        // Sem tamanho: o fim só aparece descompactando; o que sobrar volta para o stream.
        content = Inflation.inflate(in, -1, keep, maxBytes, maxScanned);
      } else if (!descriptor && (method == ZipArchive.DEFLATED || method == ZipArchive.STORED)) {
        BoundedInputStream data = new BoundedInputStream(in, compressed);
        content =
            method == ZipArchive.STORED
                ? Inflation.stored(data, compressed, keep, maxBytes)
                : Inflation.inflate(
                    new PushbackInputStream(data, Inflation.BUFFER),
                    compressed,
                    keep,
                    maxBytes,
                    maxScanned);
        if (content.overflow() || content.finished()) {
          if (!Inflation.skip(data, data.left())) {
            deliver(visitor, name, keep, content);
            return false;
          }
          content = new Inflation(content.data(), content.overflow(), true);
        }
      } else {
        return false; // sem tamanho conhecido, não há como achar a próxima entrada
      }
      if (!deliver(visitor, name, keep, content) || !content.finished()) {
        return false;
      }
      if (descriptor && !skipDescriptor(in)) {
        return false;
      }
    }
  }

  private static boolean deliver(Visitor visitor, String name, boolean keep, Inflation content)
      throws IOException {
    return visitor.visit(name, keep ? content : null);
  }

  /** Pula o descritor de dados: assinatura opcional, CRC e os dois tamanhos (32 bits). */
  private static boolean skipDescriptor(PushbackInputStream in) throws IOException {
    byte[] start = in.readNBytes(4);
    if (start.length < 4) {
      return false;
    }
    int value = ByteBuffer.wrap(start).order(ByteOrder.LITTLE_ENDIAN).getInt();
    return Inflation.skip(in, value == DATA_DESCRIPTOR ? 12 : 8);
  }
}
