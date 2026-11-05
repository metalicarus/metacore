package br.com.genosha.metacore.format.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.testkit.ResultRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Partes XML e o próprio ZIP vêm de arquivos possivelmente hostis. */
class OfficeSecurityTest {

  @TempDir Path dir;

  private static byte[] docxWithCore(String core) {
    return OfficeFixtures.office("word/document.xml", OfficeFixtures.DOCX_MAIN, core, null, null)
        .build();
  }

  @Test
  void doesNotResolveExternalEntities() throws IOException {
    String marker = "METACORE-SECRET-7d41";
    Path secret = Files.writeString(dir.resolve("secret.txt"), marker);
    String core =
        "<?xml version=\"1.0\"?><!DOCTYPE cp:coreProperties [<!ENTITY xxe SYSTEM \""
            + secret.toUri()
            + "\">]><cp:coreProperties"
            + " xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>&xxe;</dc:title>"
            + "</cp:coreProperties>";

    MetadataResult result = OoxmlParserTest.analyze(docxWithCore(core), "a.docx");

    assertThat(ResultRenderer.render(result)).doesNotContain(marker);
    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.CORRUPTED_SEGMENT);
  }

  @Test
  void refusesEntityExpansion() {
    StringBuilder entities = new StringBuilder("<!ENTITY a0 \"lol\">");
    for (int i = 1; i <= 10; i++) {
      entities
          .append("<!ENTITY a")
          .append(i)
          .append(" \"")
          .append(("&a" + (i - 1) + ";").repeat(10))
          .append("\">");
    }
    String core =
        "<?xml version=\"1.0\"?><!DOCTYPE p ["
            + entities
            + "]><cp:coreProperties"
            + " xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>&a10;</dc:title>"
            + "</cp:coreProperties>";

    MetadataResult result =
        assertTimeoutPreemptively(
            Duration.ofSeconds(5), () -> OoxmlParserTest.analyze(docxWithCore(core), "a.docx"));

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.CORRUPTED_SEGMENT);
  }

  @Test
  void stopsDecompressingAtTheLimit() {
    // 64 MiB de espaços compactam para cerca de 64 KiB.
    byte[] bomb = new byte[64 * 1024 * 1024];
    java.util.Arrays.fill(bomb, (byte) ' ');
    byte[] docx =
        OfficeFixtures.office(
                "word/document.xml", OfficeFixtures.DOCX_MAIN, null, OfficeFixtures.WORD_APP, null)
            .add("docProps/core.xml", bomb)
            .build();
    bomb = null;

    MetadataResult result =
        assertTimeoutPreemptively(
            Duration.ofSeconds(10), () -> OoxmlParserTest.analyze(docx, "bomb.docx"));

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.LIMIT_REACHED);
              assertThat(w.location()).contains("docProps/core.xml");
            });
  }
}
