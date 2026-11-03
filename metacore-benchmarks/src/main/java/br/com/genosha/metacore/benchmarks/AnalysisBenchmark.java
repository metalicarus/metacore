package br.com.genosha.metacore.benchmarks;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Tempo e alocação por análise em arquivos de tamanhos diferentes. Os arquivos são gerados no
 * setup: JPEG pequeno com EXIF, JPEG e PNG grandes com ruído (para não comprimir) e PDF com 500
 * páginas.
 *
 * <p>Uso: {@code java -jar metacore-benchmarks/target/benchmarks.jar -prof gc}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class AnalysisBenchmark {

  private final MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();

  private Path dir;
  private Path smallJpeg;
  private Path largeJpeg;
  private Path largePng;
  private Path pdf;

  @Setup(Level.Trial)
  public void createFiles() throws IOException {
    dir = Files.createTempDirectory("metacore-bench-");
    smallJpeg = Files.write(dir.resolve("small.jpg"), JpegFixtures.withGps());
    largeJpeg = Files.write(dir.resolve("large.jpg"), noise(4000, 3000, "jpg"));
    largePng = Files.write(dir.resolve("large.png"), noise(2000, 1500, "png"));
    pdf = dir.resolve("large.pdf");
    try (PDDocument document = new PDDocument()) {
      for (int i = 0; i < 500; i++) {
        document.addPage(new PDPage());
      }
      PDDocumentInformation info = document.getDocumentInformation();
      info.setTitle("Benchmark");
      info.setAuthor("MetaCore");
      document.save(pdf.toFile());
    }
  }

  @TearDown(Level.Trial)
  public void deleteFiles() throws IOException {
    try (var files = Files.list(dir)) {
      for (Path file : files.toList()) {
        Files.delete(file);
      }
    }
    Files.delete(dir);
  }

  @Benchmark
  public IdentificationResult identifyLargeJpeg() {
    return analyzer.identify(largeJpeg);
  }

  @Benchmark
  public MetadataResult analyzeSmallJpeg() {
    return analyzer.analyze(smallJpeg);
  }

  @Benchmark
  public MetadataResult analyzeLargeJpeg() {
    return analyzer.analyze(largeJpeg);
  }

  @Benchmark
  public MetadataResult analyzeLargePng() {
    return analyzer.analyze(largePng);
  }

  /** Stream maior que o limiar de memória: passa pelo spool em arquivo temporário. */
  @Benchmark
  public MetadataResult analyzeLargePngStream() throws IOException {
    try (InputStream in = Files.newInputStream(largePng)) {
      return analyzer.analyze(in, "large.png");
    }
  }

  @Benchmark
  public MetadataResult analyzePdf500Pages() {
    return analyzer.analyze(pdf);
  }

  private static byte[] noise(int width, int height, String format) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(42);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }
}
