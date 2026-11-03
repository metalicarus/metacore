package br.com.genosha.metacore.format.pdf;

import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.testkit.ContractFixture;
import br.com.genosha.metacore.testkit.MetadataParserContractTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

class PdfProviderContractTest extends MetadataParserContractTest {

  @TempDir static Path dir;

  @Override
  protected MetadataProvider provider() {
    return new PdfMetadataProvider();
  }

  @Override
  protected List<ContractFixture> fixtures() {
    return List.of(
        fixture("simple.pdf", PdfFixtures.simple()),
        fixture("info.pdf", PdfFixtures.withInfo()),
        fixture("pdf20.pdf", PdfFixtures.pdf20InfoAndXmp()));
  }

  private static ContractFixture fixture(String name, byte[] content) {
    try {
      return ContractFixture.of(Files.write(dir.resolve(name), content), "pdf");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
