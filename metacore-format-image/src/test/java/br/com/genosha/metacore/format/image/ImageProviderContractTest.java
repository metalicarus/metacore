package br.com.genosha.metacore.format.image;

import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
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
        fixture("xmp.jpg", JpegFixtures.xmpOnly()));
  }

  private static ContractFixture fixture(String name, byte[] content) {
    try {
      return ContractFixture.of(Files.write(dir.resolve(name), content), "jpeg");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
