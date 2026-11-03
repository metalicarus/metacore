package br.com.genosha.metacore.format.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.testkit.ResultRenderer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** O XMP do PDF é XML: entidades externas não podem ser resolvidas. */
class PdfSecurityTest {

  @TempDir Path dir;

  @Test
  void doesNotResolveExternalEntitiesInXmp() throws IOException {
    String marker = "METACORE-SECRET-91c2";
    Path secret = Files.writeString(dir.resolve("secret.txt"), marker);

    MetadataResult result =
        MetadataAnalyzer.createDefault()
            .analyze(
                new ByteArrayInputStream(
                    PdfFixtures.xmpWithExternalEntity(secret.toUri().toString())),
                "x.pdf");

    assertThat(ResultRenderer.render(result)).doesNotContain(marker);
    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.CORRUPTED_SEGMENT);
  }
}
