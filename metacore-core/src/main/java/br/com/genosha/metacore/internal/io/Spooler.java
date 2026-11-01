package br.com.genosha.metacore.internal.io;

import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.io.InputSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copia um {@link InputStream} para uma fonte com acesso aleatório: em memória até o limite
 * informado, em arquivo temporário acima dele (ADR 0007). O stream não é fechado.
 */
public final class Spooler {

  private static final Logger LOG = LoggerFactory.getLogger(Spooler.class);
  private static final int BUFFER_SIZE = 64 * 1024;

  private Spooler() {}

  /**
   * Copia o conteúdo.
   *
   * @throws InputLimitExceededException se o conteúdo passar de {@code maxSize}
   */
  public static SpooledInput spool(
      InputStream in, Optional<String> fileName, int memoryThreshold, long maxSize)
      throws IOException {
    int inMemoryLimit = (int) Math.min(memoryThreshold, maxSize);
    byte[] head = in.readNBytes(inMemoryLimit + 1);
    if (head.length <= inMemoryLimit) {
      return new SpooledInput(
          new ByteArrayInputSource(head, head.length, fileName), Optional.empty());
    }
    if (head.length > maxSize) {
      throw tooLarge(maxSize);
    }
    Path temp = Files.createTempFile("metacore-", ".spool");
    try {
      long total = head.length;
      try (OutputStream out = Files.newOutputStream(temp)) {
        out.write(head);
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
          total += read;
          if (total > maxSize) {
            throw tooLarge(maxSize);
          }
          out.write(buffer, 0, read);
        }
      }
      return new SpooledInput(new PathInputSource(temp, fileName), Optional.of(temp));
    } catch (IOException | RuntimeException e) {
      deleteQuietly(temp);
      throw e;
    }
  }

  private static InputLimitExceededException tooLarge(long maxSize) {
    return new InputLimitExceededException("Input exceeds maxFileSize of " + maxSize + " bytes");
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.warn("Could not delete temporary spool file {}", file, e);
    }
  }

  /** Fonte copiada e, quando houver, o arquivo temporário a apagar no {@link #close()}. */
  public record SpooledInput(InputSource source, Optional<Path> tempFile) implements Closeable {

    @Override
    public void close() {
      tempFile.ifPresent(Spooler::deleteQuietly);
    }
  }
}
