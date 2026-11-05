package br.com.genosha.metacore.format.office;

import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.testkit.ContractFixture;
import br.com.genosha.metacore.testkit.MetadataParserContractTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

class OfficeProviderContractTest extends MetadataParserContractTest {

  @TempDir static Path dir;

  @Override
  protected MetadataProvider provider() {
    return new OfficeMetadataProvider();
  }

  @Override
  protected List<ContractFixture> fixtures() {
    return List.of(
        fixture("a.docx", OfficeFixtures.docx(), "docx"),
        fixture("a.xlsx", OfficeFixtures.xlsx(), "xlsx"),
        fixture("a.pptx", OfficeFixtures.pptx(), "pptx"),
        fixture("a.zip", OfficeFixtures.plainZip(), "zip"),
        fixture("lo.pptx", OfficeFixtures.libreOffice("metadados.pptx"), "pptx"));
  }

  private static ContractFixture fixture(String name, byte[] content, String format) {
    try {
      return ContractFixture.of(Files.write(dir.resolve(name), content), format);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
