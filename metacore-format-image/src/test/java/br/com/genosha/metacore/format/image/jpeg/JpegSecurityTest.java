package br.com.genosha.metacore.format.image.jpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.format.image.fixtures.ResultRenderer;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** XMP é XML: entidades externas e expansão de entidades não podem ser processadas. */
class JpegSecurityTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  @TempDir Path dir;

  @Test
  void doesNotResolveExternalEntitiesInXmp() throws IOException {
    String marker = "METACORE-SECRET-7f3a";
    Path secret = Files.writeString(dir.resolve("secret.txt"), marker);

    MetadataResult result = analyze(JpegFixtures.xmpWithExternalEntity(secret.toUri().toString()));

    assertThat(ResultRenderer.render(result)).doesNotContain(marker);
    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .extracting(Warning::code, Warning::location)
        .contains(
            org.assertj.core.groups.Tuple.tuple(
                WarningCode.CORRUPTED_SEGMENT, java.util.Optional.of("XMP")));
  }

  @Test
  void doesNotExpandNestedEntitiesInXmp() {
    MetadataResult result =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> analyze(JpegFixtures.xmpWithEntityExpansion()));

    assertThat(ResultRenderer.render(result)).doesNotContain("lollollol");
    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
  }

  private static MetadataResult analyze(byte[] content) {
    return ANALYZER.analyze(new ByteArrayInputStream(content), "photo.jpg");
  }
}
