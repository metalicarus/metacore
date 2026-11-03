package br.com.genosha.metacore.examples;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.metadata.MetadataKeys;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Garante que os exemplos continuam funcionando com a API atual. */
class ExamplesTest {

  @TempDir Path dir;

  @Test
  void inspectFileDescribesAPhoto() throws IOException {
    Path photo = Files.write(dir.resolve("photo.jpg"), JpegFixtures.withGps());

    assertThat(InspectFile.describe(photo))
        .contains(
            "format: JPEG (image/jpeg)",
            "status: SUCCESS",
            "captured at 2026-07-14T19:32:05Z (UTC)")
        .anyMatch(line -> line.startsWith("location.latitude = "));
  }

  @Test
  void analyzeUploadAcceptsImagesAndRejectsEverythingElse() {
    assertThat(AnalyzeUpload.check(new ByteArrayInputStream(JpegFixtures.basicExif()), "a.jpg"))
        .isEqualTo(new AnalyzeUpload.Verdict(true, "accepted", Optional.of("64x48")));
    assertThat(
            AnalyzeUpload.check(new ByteArrayInputStream("<html>".getBytes()), "a.jpg").accepted())
        .isFalse();
    byte[] truncated = JpegFixtures.truncate(JpegFixtures.basicExif(), 400);
    assertThat(AnalyzeUpload.check(new ByteArrayInputStream(truncated), "a.jpg").reason())
        .isEqualTo("accepted with warnings");
  }

  @Test
  void customFormatIsDetectedAndParsed() throws IOException {
    Path notes =
        Files.writeString(
            dir.resolve("meeting.notes"), "#notes\ntitle: Planning\nauthor: Ana\nbroken line\n");

    MetadataResult result = CustomFormat.analyzer().analyze(notes);

    assertThat(result.format()).contains(CustomFormat.NOTES);
    // A linha sem "chave: valor" é perda de dados: o resultado fica PARTIAL, com o resto lido.
    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.find(MetadataKeys.DOCUMENT_TITLE).orElseThrow().value())
        .isEqualTo(MetadataValue.of("Planning"));
    assertThat(result.findRaw("NOTES", "author")).hasSize(1);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.CORRUPTED_SEGMENT);
  }
}
