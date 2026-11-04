package br.com.genosha.metacore.internal.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.genosha.metacore.exception.InputLimitExceededException;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StreamInputSourceTest {

  private static final byte[] CONTENT = randomBytes(300_000);

  @Test
  void readsOnlyWhatIsRequested() throws IOException {
    CountingStream in = new CountingStream(CONTENT);
    try (StreamInputSource source = source(in, 1 << 20, Long.MAX_VALUE)) {
      try (InputStream stream = source.openStream()) {
        assertThat(stream.readNBytes(10)).isEqualTo(Arrays.copyOf(CONTENT, 10));
      }
      assertThat(in.consumed).isLessThan(CONTENT.length);
      assertThat(source.size()).isEmpty();
    }
  }

  @Test
  void everyStreamStartsFromTheBeginning() throws IOException {
    try (StreamInputSource source = source(new CountingStream(CONTENT), 1 << 20, Long.MAX_VALUE)) {
      byte[] first;
      try (InputStream stream = source.openStream()) {
        first = stream.readNBytes(100_000);
      }
      try (InputStream stream = source.openStream()) {
        assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
      }
      assertThat(first).isEqualTo(Arrays.copyOf(CONTENT, 100_000));
      assertThat(source.size()).hasValue(CONTENT.length);
    }
  }

  @Test
  void skippedBytesRemainAvailableToLaterReaders() throws IOException {
    try (StreamInputSource source = source(new CountingStream(CONTENT), 1 << 20, Long.MAX_VALUE)) {
      try (InputStream stream = source.openStream()) {
        assertThat(stream.skip(200_000)).isEqualTo(200_000);
        assertThat(stream.read()).isEqualTo(CONTENT[200_000] & 0xFF);
      }
      try (InputStream stream = source.openStream()) {
        assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
      }
    }
  }

  @Test
  void spillsToATemporaryFileAboveTheThresholdAndDeletesItOnClose() throws IOException {
    Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
    long before = countSpoolFiles(tempDir);
    StreamInputSource source = source(new CountingStream(CONTENT), 1000, Long.MAX_VALUE);
    try (SeekableByteChannel channel = source.openChannel()) {
      assertThat(countSpoolFiles(tempDir)).isEqualTo(before + 1);
      assertThat(channel.size()).isEqualTo(CONTENT.length);
      // leitura que atravessa a fronteira entre memória e arquivo
      ByteBuffer buffer = ByteBuffer.allocate(100);
      channel.position(950);
      while (buffer.hasRemaining() && channel.read(buffer) > 0) {
        // completa o buffer
      }
      assertThat(buffer.array()).isEqualTo(Arrays.copyOfRange(CONTENT, 950, 1050));
      channel.position(CONTENT.length - 5);
      ByteBuffer tail = ByteBuffer.allocate(10);
      assertThat(channel.read(tail)).isEqualTo(5);
      assertThat(channel.read(tail)).isEqualTo(-1);
    }
    try (InputStream stream = source.openStream()) {
      assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
    }
    source.close();
    assertThat(countSpoolFiles(tempDir)).isEqualTo(before);
  }

  @Test
  void stopsAtTheMaximumSize() throws IOException {
    try (StreamInputSource source = source(new CountingStream(CONTENT), 1 << 20, 100_000)) {
      try (InputStream stream = source.openStream()) {
        assertThat(stream.readNBytes(1000)).hasSize(1000);
      }
      assertThatThrownBy(source::openChannel).isInstanceOf(InputLimitExceededException.class);
    }
  }

  @Test
  void neverClosesTheCallersStream() throws IOException {
    CountingStream in = new CountingStream(CONTENT);
    try (StreamInputSource source = source(in, 1000, Long.MAX_VALUE)) {
      source.openStream().close();
      source.openChannel().close();
    }
    assertThat(in.closed).isFalse();
  }

  @Test
  void emptyStreamHasSizeZero() throws IOException {
    try (StreamInputSource source = source(new CountingStream(new byte[0]), 1000, Long.MAX_VALUE)) {
      assertThat(source.openStream().read()).isEqualTo(-1);
      assertThat(source.size()).hasValue(0);
    }
  }

  private static StreamInputSource source(InputStream in, int threshold, long maxSize) {
    return new StreamInputSource(in, Optional.of("a.bin"), threshold, maxSize);
  }

  private static long countSpoolFiles(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
      return files.filter(f -> f.getFileName().toString().startsWith("metacore-")).count();
    }
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(7).nextBytes(bytes);
    return bytes;
  }

  /** Conta quantos bytes foram lidos e se o stream foi fechado. */
  private static final class CountingStream extends FilterInputStream {
    long consumed;
    boolean closed;

    CountingStream(byte[] content) {
      super(new ByteArrayInputStream(content));
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value != -1) {
        consumed++;
      }
      return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int count = super.read(b, off, len);
      if (count > 0) {
        consumed += count;
      }
      return count;
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
