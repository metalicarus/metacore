package br.com.genosha.metacore.format.office.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * Descompactação DEFLATE com teto de saída. Guarda no máximo {@code maxBytes}; o resto é
 * descompactado só quando é preciso achar o fim dos dados, e nunca além de {@code maxScanned}.
 *
 * @param data o que foi guardado
 * @param overflow se havia mais que {@code maxBytes}
 * @param finished se o fim dos dados compactados foi encontrado
 */
public record Inflation(byte[] data, boolean overflow, boolean finished) {

  static final int BUFFER = 8192;

  /**
   * Descompacta de {@code in}.
   *
   * @param compressedSize bytes compactados disponíveis, ou -1 quando o tamanho é desconhecido e o
   *     fim precisa ser achado pelo próprio DEFLATE; nesse caso, o que sobrar da leitura volta para
   *     {@code in}
   * @param keep se a saída deve ser guardada
   * @param maxBytes quanto da saída guardar
   * @param maxScanned teto da saída total; acima dele, para com {@code finished} falso
   */
  static Inflation inflate(
      PushbackInputStream in, long compressedSize, boolean keep, int maxBytes, long maxScanned)
      throws IOException {
    Inflater inflater = new Inflater(true);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] input = new byte[BUFFER];
    byte[] output = new byte[BUFFER];
    long consumed = 0;
    long produced = 0;
    int lastRead = 0;
    boolean dummy = false;
    boolean overflow = false;
    try {
      while (!inflater.finished()) {
        if (inflater.needsInput()) {
          int want =
              compressedSize < 0
                  ? input.length
                  : (int) Math.min(input.length, compressedSize - consumed);
          if (want == 0) {
            if (dummy) {
              break;
            }
            // O modo sem cabeçalho do zlib pode pedir um byte além dos dados.
            dummy = true;
            lastRead = 0;
            inflater.setInput(new byte[1]);
            continue;
          }
          int read = in.read(input, 0, want);
          if (read < 0) {
            break; // truncado
          }
          lastRead = read;
          consumed += read;
          inflater.setInput(input, 0, read);
        }
        if (inflater.needsDictionary()) {
          throw new ZipException("DEFLATE stream needs a preset dictionary");
        }
        int count = inflater.inflate(output);
        if (count == 0 && !inflater.needsInput() && !inflater.finished()) {
          throw new ZipException("DEFLATE stream made no progress");
        }
        produced += count;
        if (keep && !overflow) {
          int room = maxBytes - out.size();
          out.write(output, 0, Math.min(room, count));
          overflow = count > room;
          if (overflow && compressedSize >= 0) {
            return new Inflation(out.toByteArray(), true, false);
          }
        }
        if (produced > maxScanned) {
          return new Inflation(out.toByteArray(), overflow, false);
        }
      }
      boolean finished = inflater.finished();
      if (finished && compressedSize < 0 && lastRead > 0) {
        int remaining = inflater.getRemaining();
        in.unread(input, lastRead - remaining, remaining);
      }
      return new Inflation(out.toByteArray(), overflow, finished);
    } catch (DataFormatException e) {
      throw new ZipException("Invalid DEFLATE data: " + e.getMessage());
    } finally {
      inflater.end();
    }
  }

  /** Lê até {@code maxBytes} de dados guardados sem compactação. */
  static Inflation stored(InputStream in, long size, boolean keep, int maxBytes)
      throws IOException {
    long kept = keep ? Math.min(size, maxBytes) : 0;
    byte[] data = in.readNBytes((int) kept);
    if (data.length < kept) {
      return new Inflation(data, false, false);
    }
    boolean complete = skip(in, size - kept);
    return new Inflation(data, size > maxBytes && keep, complete);
  }

  /** Pula {@code count} bytes e diz se havia todos. */
  static boolean skip(InputStream in, long count) throws IOException {
    long left = count;
    byte[] buffer = null;
    while (left > 0) {
      long skipped = in.skip(left);
      if (skipped > 0) {
        left -= skipped;
        continue;
      }
      if (buffer == null) {
        buffer = new byte[BUFFER];
      }
      int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
      if (read < 0) {
        return false;
      }
      left -= read;
    }
    return true;
  }
}
