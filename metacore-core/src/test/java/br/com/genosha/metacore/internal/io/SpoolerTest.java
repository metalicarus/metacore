package br.com.genosha.metacore.internal.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.internal.io.Spooler.SpooledInput;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpoolerTest {

  private static final byte[] CONTENT = "0123456789".getBytes();

  @Test
  void keepsSmallInputsInMemory() throws IOException {
    try (SpooledInput spooled = Spooler.spool(stream(), Optional.of("a.txt"), 64, 1000)) {
      assertThat(spooled.tempFile()).isEmpty();
      assertThat(spooled.source().size()).isEqualTo(CONTENT.length);
      assertThat(spooled.source().fileName()).contains("a.txt");
      assertThat(readAll(spooled.source().openStream())).isEqualTo(CONTENT);
    }
  }

  @Test
  void spoolsLargeInputsToATemporaryFileDeletedOnClose() throws IOException {
    Path temp;
    try (SpooledInput spooled = Spooler.spool(stream(), Optional.of("a.txt"), 4, 1000)) {
      temp = spooled.tempFile().orElseThrow();
      assertThat(temp).exists();
      assertThat(spooled.source().fileName()).contains("a.txt");
      assertThat(readAll(spooled.source().openStream())).isEqualTo(CONTENT);
    }
    assertThat(temp).doesNotExist();
  }

  @Test
  void rejectsInputsAboveTheMaximumSize() {
    assertThatThrownBy(() -> Spooler.spool(stream(), Optional.empty(), 64, 5))
        .isInstanceOf(InputLimitExceededException.class);
    assertThatThrownBy(() -> Spooler.spool(stream(), Optional.empty(), 2, 5))
        .isInstanceOf(InputLimitExceededException.class);
  }

  @Test
  void acceptsInputsExactlyAtTheMaximumSize() throws IOException {
    try (SpooledInput spooled = Spooler.spool(stream(), Optional.empty(), 2, CONTENT.length)) {
      assertThat(spooled.source().size()).isEqualTo(CONTENT.length);
    }
  }

  @Test
  void doesNotCloseTheCallersStream() throws IOException {
    CloseTrackingStream in = new CloseTrackingStream(stream());

    Spooler.spool(in, Optional.empty(), 64, 1000).close();

    assertThat(in.closed).isFalse();
  }

  @Test
  void channelsSupportRandomAccessInMemoryAndOnDisk() throws IOException {
    for (int threshold : new int[] {64, 4}) {
      try (SpooledInput spooled = Spooler.spool(stream(), Optional.empty(), threshold, 1000);
          SeekableByteChannel channel = spooled.source().openChannel()) {
        ByteBuffer buffer = ByteBuffer.allocate(3);
        channel.position(7);
        channel.read(buffer);
        assertThat(new String(buffer.array())).isEqualTo("789");
        assertThat(channel.size()).isEqualTo(CONTENT.length);
      }
    }
  }

  private static InputStream stream() {
    return new ByteArrayInputStream(CONTENT);
  }

  private static byte[] readAll(InputStream in) throws IOException {
    try (in) {
      return in.readAllBytes();
    }
  }

  @Test
  void temporaryFileIsNotLeftBehindOnFailure() throws IOException {
    Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
    long before = countSpoolFiles(tempDir);

    assertThatThrownBy(() -> Spooler.spool(stream(), Optional.empty(), 2, 5))
        .isInstanceOf(InputLimitExceededException.class);

    assertThat(countSpoolFiles(tempDir)).isLessThanOrEqualTo(before);
  }

  private static long countSpoolFiles(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
      return files.filter(f -> f.getFileName().toString().startsWith("metacore-")).count();
    }
  }

  private static final class CloseTrackingStream extends FilterInputStream {
    boolean closed;

    CloseTrackingStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
