package br.com.genosha.metacore.format.image;

import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.format.image.fixtures.PngFixtures;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.testkit.ContractFixture;
import br.com.genosha.metacore.testkit.MetadataParserContractTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

class ImageProviderContractTest extends MetadataParserContractTest {

  @TempDir static Path dir;

  @Override
  protected MetadataProvider provider() {
    return new ImageMetadataProvider();
  }

  @Override
  protected List<ContractFixture> fixtures() {
    return List.of(
        fixture("plain.jpg", JpegFixtures.plain()),
        fixture("exif.jpg", JpegFixtures.basicExif()),
        fixture("gps.jpg", JpegFixtures.withGps()),
        fixture("xmp.jpg", JpegFixtures.xmpOnly()),
        fixture("plain.png", PngFixtures.plain(), "png"),
        fixture("text.png", PngFixtures.withText(), "png"),
        fixture("exif.png", PngFixtures.withExif(), "png"),
        fixture("xmp.png", PngFixtures.withXmp(), "png"));
  }

  private static ContractFixture fixture(String name, byte[] content) {
    return fixture(name, content, "jpeg");
  }

  private static ContractFixture fixture(String name, byte[] content, String formatId) {
    try {
      return ContractFixture.of(Files.write(dir.resolve(name), content), formatId);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
