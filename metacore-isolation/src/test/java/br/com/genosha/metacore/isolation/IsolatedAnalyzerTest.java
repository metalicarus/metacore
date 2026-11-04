package br.com.genosha.metacore.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.format.image.fixtures.PngFixtures;
import br.com.genosha.metacore.format.pdf.PdfFixtures;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.Warning;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Sobe workers de verdade: cada teste paga a subida de uma JVM. */
class IsolatedAnalyzerTest {

  private static final MetadataAnalyzer IN_PROCESS = MetadataAnalyzer.createDefault();
  private static IsolatedAnalyzer shared;

  @TempDir Path dir;

  @BeforeAll
  static void startShared() {
    shared = IsolatedAnalyzer.builder().timeout(Duration.ofSeconds(20)).build();
  }

  @AfterAll
  static void closeShared() {
    shared.close();
  }

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("exif.jpg", JpegFixtures.withGps()),
        Arguments.of("xmp.jpg", JpegFixtures.withConflictingXmp()),
        Arguments.of("truncated.jpg", JpegFixtures.truncate(JpegFixtures.basicExif(), 200)),
        Arguments.of("text.png", PngFixtures.withText()),
        Arguments.of("xmp.png", PngFixtures.withXmp()),
        Arguments.of("info.pdf", PdfFixtures.withInfo()),
        Arguments.of("xmp.pdf", PdfFixtures.pdf20InfoAndXmp()),
        Arguments.of("unknown.bin", new byte[] {1, 2, 3, 4}));
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void givesTheSameResultAsTheAnalysisInProcess(String name, byte[] content) throws IOException {
    Path file = Files.write(dir.resolve(name), content);

    assertThat(withoutDuration(shared.analyze(file)))
        .isEqualTo(withoutDuration(IN_PROCESS.analyze(file)));
    assertThat(withoutDuration(shared.analyze(new ByteArrayInputStream(content), name)))
        .isEqualTo(withoutDuration(IN_PROCESS.analyze(new ByteArrayInputStream(content), name)));
  }

  @Test
  void runsInAnotherProcessAndReusesIt() throws IOException {
    Path file = Files.write(dir.resolve("pid.mctest"), MisbehavingProvider.content("pid"));

    long first = pid(shared.analyze(file));
    long second = pid(shared.analyze(file));

    assertThat(first).isNotEqualTo(ProcessHandle.current().pid()).isEqualTo(second);
  }

  @Test
  void replacesWorkersAfterMaxAnalyses() throws IOException {
    Path file = Files.write(dir.resolve("pid.mctest"), MisbehavingProvider.content("pid"));
    try (IsolatedAnalyzer analyzer = IsolatedAnalyzer.builder().maxAnalysesPerWorker(1).build()) {
      assertThat(pid(analyzer.analyze(file))).isNotEqualTo(pid(analyzer.analyze(file)));
    }
  }

  @Test
  void killsAnAnalysisThatPassesTheTimeout() throws IOException {
    Path hang = Files.write(dir.resolve("hang.mctest"), MisbehavingProvider.content("hang"));
    Path pid = Files.write(dir.resolve("pid.mctest"), MisbehavingProvider.content("pid"));
    try (IsolatedAnalyzer analyzer =
        IsolatedAnalyzer.builder().timeout(Duration.ofSeconds(2)).build()) {
      long start = System.nanoTime();
      MetadataResult result = analyzer.analyze(hang);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

      assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
      assertThat(codes(result)).containsExactly("TIMEOUT");
      assertThat(result.detection()).isEmpty();
      assertThat(result.file().name()).contains("hang.mctest");
      assertThat(result.file().size()).hasValue(Files.size(hang));
      assertThat(elapsed).isLessThan(Duration.ofSeconds(15));

      // O worker seguinte é novo e funciona.
      assertThat(analyzer.analyze(pid).status()).isEqualTo(ExtractionStatus.SUCCESS);
    }
  }

  @Test
  void survivesAWorkerThatRunsOutOfMemory() throws IOException {
    Path file = Files.write(dir.resolve("oom.mctest"), MisbehavingProvider.content("oom"));
    try (IsolatedAnalyzer analyzer =
        IsolatedAnalyzer.builder().maxHeapSize(32L * 1024 * 1024).build()) {
      MetadataResult result = analyzer.analyze(file);

      assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
      assertThat(result.warnings())
          .singleElement()
          .satisfies(
              w -> {
                assertThat(w.code()).isEqualTo(IsolationWarnings.WORKER_FAILED);
                assertThat(w.message()).contains("exited with code 3").contains("OutOfMemoryError");
              });
    }
  }

  @Test
  void survivesAParserThatExitsTheJvm() throws IOException {
    Path file = Files.write(dir.resolve("exit.mctest"), MisbehavingProvider.content("exit"));

    MetadataResult result = shared.analyze(file);

    assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
    assertThat(codes(result)).containsExactly("WORKER_FAILED");
    assertThat(result.warnings().get(0).message()).contains("exited with code 42");
  }

  @Test
  void keepsTheProtocolWhenAParserWritesToStandardOutput() throws IOException {
    Path file = Files.write(dir.resolve("out.mctest"), MisbehavingProvider.content("stdout"));

    assertThat(shared.analyze(file).status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(shared.analyze(file).status()).isEqualTo(ExtractionStatus.SUCCESS);
  }

  @Test
  void rethrowsTheExceptionsOfTheAnalysisInProcess() throws IOException {
    assertThatThrownBy(() -> shared.analyze(dir.resolve("missing.jpg")))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("File not found");

    Path big = Files.write(dir.resolve("big.jpg"), JpegFixtures.basicExif());
    try (IsolatedAnalyzer analyzer =
        IsolatedAnalyzer.builder().limits(AnalysisLimits.defaults().withMaxFileSize(100)).build()) {
      assertThatThrownBy(() -> analyzer.analyze(big))
          .isInstanceOf(InputLimitExceededException.class);
      // A recusa não estraga o worker.
      assertThatThrownBy(() -> analyzer.analyze(big))
          .isInstanceOf(InputLimitExceededException.class);
    }
  }

  @Test
  void readsStreamsOnDemand() {
    byte[] jpeg = JpegFixtures.basicExif();
    long total = 1L << 30;
    CountingStream in = new CountingStream(jpeg, total);

    MetadataResult result = shared.analyze(in, "big.jpg");

    assertThat(result.format()).hasValueSatisfying(f -> assertThat(f.id()).isEqualTo("jpeg"));
    assertThat(in.read).isLessThan(16L * 1024 * 1024);
  }

  @Test
  void reportsAFailingStreamAsTheAnalysisInProcessDoes() {
    InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("disk on fire");
          }
        };

    assertThatThrownBy(() -> shared.analyze(failing, "x.jpg"))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  void runsAnalysesInParallel() throws Exception {
    Path file = Files.write(dir.resolve("exif.jpg"), JpegFixtures.withGps());
    try (IsolatedAnalyzer analyzer = IsolatedAnalyzer.builder().workers(2).build();
        ExecutorService pool = Executors.newFixedThreadPool(4)) {
      List<Future<MetadataResult>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        futures.add(pool.submit(() -> analyzer.analyze(file)));
      }
      for (Future<MetadataResult> future : futures) {
        assertThat(future.get().status()).isEqualTo(ExtractionStatus.SUCCESS);
      }
    }
  }

  @Test
  void failsWhenTheWorkerCannotStart() throws IOException {
    Path file = Files.write(dir.resolve("exif.jpg"), JpegFixtures.plain());

    try (IsolatedAnalyzer analyzer =
        IsolatedAnalyzer.builder().javaExecutable(dir.resolve("no-java")).build()) {
      assertThatThrownBy(() -> analyzer.analyze(file))
          .isInstanceOf(IsolationException.class)
          .hasMessageContaining("Could not start worker process");
    }
    try (IsolatedAnalyzer analyzer = IsolatedAnalyzer.builder().classPath(dir.toString()).build()) {
      assertThatThrownBy(() -> analyzer.analyze(file))
          .isInstanceOf(IsolationException.class)
          .hasMessageContaining("exited with code 1");
    }
  }

  @Test
  void refusesAnalysesAfterClose() throws IOException {
    Path file = Files.write(dir.resolve("exif.jpg"), JpegFixtures.plain());
    IsolatedAnalyzer analyzer = IsolatedAnalyzer.builder().build();
    analyzer.close();

    assertThatThrownBy(() -> analyzer.analyze(file)).isInstanceOf(IllegalStateException.class);
  }

  private static MetadataResult withoutDuration(MetadataResult result) {
    return new MetadataResult(
        result.file(),
        result.detection(),
        result.status(),
        result.metadata(),
        result.warnings(),
        Duration.ZERO);
  }

  private static long pid(MetadataResult result) {
    return ((IntegerValue) result.findRaw("MCTEST", "pid").get(0).value()).value();
  }

  private static List<String> codes(MetadataResult result) {
    return result.warnings().stream().map(Warning::code).map(c -> c.value()).toList();
  }

  /** Um JPEG seguido de zeros até {@code total} bytes, gerados sob demanda. */
  private static final class CountingStream extends InputStream {
    private final byte[] head;
    private final long total;
    long read;

    CountingStream(byte[] head, long total) {
      this.head = head;
      this.total = total;
    }

    @Override
    public int read() {
      if (read >= total) {
        return -1;
      }
      int value = read < head.length ? head[(int) read] & 0xFF : 0;
      read++;
      return value;
    }

    @Override
    public int read(byte[] b, int off, int len) {
      if (read >= total) {
        return -1;
      }
      int count = (int) Math.min(len, total - read);
      for (int i = 0; i < count; i++) {
        long position = read + i;
        b[off + i] = position < head.length ? head[(int) position] : 0;
      }
      read += count;
      return count;
    }
  }
}
