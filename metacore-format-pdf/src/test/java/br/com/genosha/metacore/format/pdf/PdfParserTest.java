package br.com.genosha.metacore.format.pdf;

import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_AUTHOR;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_CREATED;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_FORMAT_VERSION;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_KEYWORDS;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_MODIFIED;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_PAGE_COUNT;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_TITLE;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfParserTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  @TempDir Path dir;

  @Test
  void readsVersionAndPageCount() {
    MetadataResult result = analyze(PdfFixtures.simple());

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(value(result, DOCUMENT_FORMAT_VERSION)).isEqualTo(MetadataValue.of("1.7"));
    assertThat(value(result, DOCUMENT_PAGE_COUNT)).isEqualTo(MetadataValue.of(1));
  }

  @Test
  void readsTheInfoDictionaryWithTypedValues() {
    MetadataResult result = analyze(PdfFixtures.withInfo());

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(value(result, DOCUMENT_PAGE_COUNT)).isEqualTo(MetadataValue.of(3));
    assertThat(value(result, DOCUMENT_TITLE)).isEqualTo(MetadataValue.of("Relatório anual"));
    assertThat(value(result, DOCUMENT_AUTHOR)).isEqualTo(MetadataValue.of("Genosha"));
    assertThat(value(result, DOCUMENT_KEYWORDS))
        .isEqualTo(
            new ListValue(
                List.of(
                    MetadataValue.of("pdf"),
                    MetadataValue.of("metadata"),
                    MetadataValue.of("testes"))));
    assertThat(value(result, DOCUMENT_CREATED))
        .isEqualTo(
            MetadataValue.of(OffsetDateTime.of(2026, 7, 14, 16, 32, 5, 0, ZoneOffset.ofHours(-3))));
    assertThat(value(result, DOCUMENT_MODIFIED))
        .isEqualTo(MetadataValue.of(OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, ZoneOffset.UTC)));
    assertThat(result.findRaw("PDF:Info", "Department"))
        .singleElement()
        .extracting(Metadata::value)
        .isEqualTo(MetadataValue.of("Engenharia"));
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void keepsDatesWithoutOffsetLocal() {
    assertThat(value(analyze(PdfFixtures.dateWithoutOffset()), DOCUMENT_CREATED))
        .isEqualTo(MetadataValue.of(LocalDateTime.of(2026, 7, 14, 16, 32, 5)));
  }

  @Test
  void keepsUnconvertibleDatesAsRawFields() {
    MetadataResult result = analyze(PdfFixtures.invalidDate());

    assertThat(result.find(DOCUMENT_CREATED)).isEmpty();
    assertThat(result.findRaw("PDF:Info", "CreationDate")).hasSize(1);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.VALUE_CONVERSION_FAILED);
  }

  @Test
  void infoWinsOverXmpBeforePdf20() {
    MetadataResult result = analyze(PdfFixtures.infoAndXmp());

    assertThat(value(result, DOCUMENT_TITLE)).isEqualTo(MetadataValue.of("Info Title"));
    assertThat(value(result, DOCUMENT_AUTHOR)).isEqualTo(MetadataValue.of("Genosha"));
    assertThat(result.findAll(DOCUMENT_TITLE)).hasSize(2);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.METADATA_CONFLICT);
  }

  @Test
  void xmpWinsOverInfoInPdf20() {
    MetadataResult result = analyze(PdfFixtures.pdf20InfoAndXmp());

    assertThat(value(result, DOCUMENT_FORMAT_VERSION)).isEqualTo(MetadataValue.of("2.0"));
    assertThat(value(result, DOCUMENT_TITLE)).isEqualTo(MetadataValue.of("XMP Title"));
    assertThat(result.find(DOCUMENT_TITLE).orElseThrow().namespace()).isEqualTo("XMP");
  }

  @Test
  void reportsDocumentsThatNeedAPassword() {
    MetadataResult result = analyze(PdfFixtures.userPassword());

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.find(DOCUMENT_TITLE)).isEmpty();
    assertThat(result.findRaw("PDF", "Encrypted"))
        .singleElement()
        .extracting(Metadata::value)
        .isEqualTo(MetadataValue.of(true));
    assertThat(result.warnings()).extracting(Warning::code).containsExactly(WarningCode.ENCRYPTED);
  }

  @Test
  void readsDocumentsEncryptedOnlyWithAnOwnerPassword() {
    MetadataResult result = analyze(PdfFixtures.ownerPasswordOnly());

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(value(result, DOCUMENT_TITLE)).isEqualTo(MetadataValue.of("Protected"));
    assertThat(result.findRaw("PDF", "Encrypted")).hasSize(1);
  }

  @Test
  void reportsAMissingEndOfFileMarker() {
    byte[] full = PdfFixtures.withInfo();
    MetadataResult result = analyze(PdfFixtures.truncate(full, full.length - 30));

    assertThat(result.status()).isIn(ExtractionStatus.PARTIAL, ExtractionStatus.FAILED);
    assertThat(result.warnings()).extracting(Warning::code).contains(WarningCode.TRUNCATED);
  }

  @Test
  void readsFromFilesThroughRandomAccess() throws IOException {
    Path file = Files.write(dir.resolve("report.pdf"), PdfFixtures.withInfo());

    MetadataResult result = ANALYZER.analyze(file);

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(value(result, DOCUMENT_PAGE_COUNT)).isEqualTo(MetadataValue.of(3));
  }

  private static MetadataResult analyze(byte[] content) {
    return ANALYZER.analyze(new ByteArrayInputStream(content), "document.pdf");
  }

  private static MetadataValue value(MetadataResult result, String key) {
    return result.find(key).orElseThrow(() -> new AssertionError("missing " + key)).value();
  }
}
