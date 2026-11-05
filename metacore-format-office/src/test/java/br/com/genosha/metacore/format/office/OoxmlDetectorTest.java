package br.com.genosha.metacore.format.office;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OoxmlDetectorTest {

  private static final OoxmlDetector DETECTOR = new OoxmlDetector();
  private static final int HEAD = 8 * 1024;

  private static Optional<DetectionCandidate> detect(byte[] content) {
    return DETECTOR.detect(
        DetectionRequest.of(
            Arrays.copyOf(content, Math.min(HEAD, content.length)), Optional.empty()));
  }

  @Test
  void usesTheMainContentTypeWhenContentTypesComesFirst() {
    assertThat(detect(OfficeFixtures.docx()))
        .contains(
            new DetectionCandidate(
                "docx",
                Confidence.EXACT,
                java.util.EnumSet.of(Evidence.MAGIC, Evidence.STRUCTURE)));
    assertThat(detect(OfficeFixtures.xlsx())).map(DetectionCandidate::formatId).contains("xlsx");
    assertThat(detect(OfficeFixtures.pptx())).map(DetectionCandidate::formatId).contains("pptx");
    assertThat(detect(OfficeFixtures.docm())).map(DetectionCandidate::formatId).contains("docx");
  }

  @Test
  void findsTheTypeInLibreOfficeFiles() {
    assertThat(detect(OfficeFixtures.libreOffice("metadados.docx")))
        .map(DetectionCandidate::formatId)
        .contains("docx");
    assertThat(detect(OfficeFixtures.libreOffice("metadados.xlsx")))
        .map(DetectionCandidate::formatId)
        .contains("xlsx");
    // Com 11 KiB, o [Content_Types].xml fica fora do início lido; a parte principal decide.
    assertThat(detect(OfficeFixtures.libreOffice("metadados.pptx")))
        .hasValueSatisfying(
            c -> {
              assertThat(c.formatId()).isEqualTo("pptx");
              assertThat(c.confidence()).isEqualTo(Confidence.HIGH);
            });
    assertThat(detect(OfficeFixtures.docxWithDescriptors()))
        .map(DetectionCandidate::formatId)
        .contains("docx");
  }

  @Test
  void recognizesPlainZipFiles() {
    assertThat(detect(OfficeFixtures.plainZip()))
        .contains(
            new DetectionCandidate(
                "zip", Confidence.EXACT, java.util.EnumSet.of(Evidence.MAGIC, Evidence.STRUCTURE)));
    byte[] empty = new ZipBuilder().build();
    assertThat(detect(empty))
        .contains(DetectionCandidate.of("zip", Confidence.HIGH, Evidence.MAGIC));
  }

  @Test
  void doesNotTrustFolderNamesWithoutAPackagePart() {
    byte[] folder = new ZipBuilder().add("xl/notes.txt", "x").add("word/a.txt", "y").build();

    assertThat(detect(folder)).map(DetectionCandidate::formatId).contains("zip");
  }

  @Test
  void ignoresOtherContent() {
    assertThat(detect("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII))).isEmpty();
    assertThat(detect(new byte[] {'P', 'K'})).isEmpty();
  }

  @Test
  void survivesTruncatedAndCorruptHeads() {
    byte[] docx = OfficeFixtures.docx();
    for (int length = 4; length < docx.length; length += 7) {
      assertThat(detect(Arrays.copyOf(docx, length))).as("length %d", length).isPresent();
    }
    byte[] corrupt = docx.clone();
    Arrays.fill(corrupt, 30, 200, (byte) 0x55);
    assertThat(detect(corrupt)).map(DetectionCandidate::formatId).contains("zip");
  }
}
