package br.com.genosha.metacore.comparison;

import br.com.genosha.metacore.comparison.Fixtures.Fixture;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** As mesmas entradas do benchmark JMH (docs/performance.md). */
final class PerfInputs {

  private PerfInputs() {}

  static Map<String, Fixture> create() throws IOException {
    Map<String, Fixture> inputs = new LinkedHashMap<>();
    inputs.put(
        "JPEG 64x48 com EXIF e GPS", new Fixture("small", "small.jpg", JpegFixtures.withGps()));
    inputs.put(
        "JPEG 4000x3000 (~4 MB)", new Fixture("large", "large.jpg", noise(4000, 3000, "jpg")));
    inputs.put(
        "PNG 2000x1500 (~9 MB)", new Fixture("large", "large.png", noise(2000, 1500, "png")));
    inputs.put("PDF com 500 páginas", new Fixture("pdf", "large.pdf", pdf(500)));
    return inputs;
  }

  private static byte[] noise(int width, int height, String format) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(42);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private static byte[] pdf(int pages) throws IOException {
    try (PDDocument document = new PDDocument()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      document.getDocumentInformation().setTitle("Benchmark");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
