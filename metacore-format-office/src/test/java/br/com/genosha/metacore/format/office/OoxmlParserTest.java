package br.com.genosha.metacore.format.office;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.metadata.BooleanValue;
import br.com.genosha.metacore.metadata.DecimalValue;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import br.com.genosha.metacore.metadata.TextValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OoxmlParserTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  static MetadataResult analyze(byte[] content, String name) {
    return ANALYZER.analyze(new ByteArrayInputStream(content), name);
  }

  static MetadataValue primary(MetadataResult result, String key) {
    return result.metadata().stream()
        .filter(m -> m.primary() && m.key().filter(key::equals).isPresent())
        .map(Metadata::value)
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing " + key));
  }

  static MetadataValue raw(MetadataResult result, String namespace, String rawName) {
    return result.findRaw(namespace, rawName).stream()
        .map(Metadata::value)
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing " + namespace + " " + rawName));
  }

  @Test
  void mapsTheCoreProperties() {
    MetadataResult result = analyze(OfficeFixtures.docx(), "a.docx");

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.warnings()).isEmpty();
    assertThat(primary(result, "document.title")).isEqualTo(MetadataValue.of("Relatório anual"));
    assertThat(primary(result, "document.author")).isEqualTo(MetadataValue.of("Genosha"));
    assertThat(primary(result, "document.subject")).isEqualTo(MetadataValue.of("Metadados"));
    assertThat(primary(result, "document.last_modified_by")).isEqualTo(MetadataValue.of("Revisor"));
    assertThat(primary(result, "document.keywords"))
        .isEqualTo(
            new ListValue(
                List.of(
                    MetadataValue.of("office"),
                    MetadataValue.of("metadados"),
                    MetadataValue.of("testes"))));
    assertThat(primary(result, "document.created"))
        .isEqualTo(MetadataValue.of(OffsetDateTime.of(2026, 7, 14, 16, 32, 5, 0, ZoneOffset.UTC)));
    assertThat(primary(result, "document.modified"))
        .isEqualTo(
            MetadataValue.of(OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, ZoneOffset.ofHours(-3))));
    assertThat(raw(result, "OOXML:Core", "dc:description"))
        .isEqualTo(MetadataValue.of("Arquivo de teste"));
    assertThat(raw(result, "OOXML:Core", "cp:lastPrinted")).isInstanceOf(OffsetDateTimeValue.class);
    assertThat(raw(result, "OOXML:Core", "cp:category")).isEqualTo(MetadataValue.of("Relatórios"));
  }

  @Test
  void readsTheExtendedProperties() {
    MetadataResult word = analyze(OfficeFixtures.docx(), "a.docx");
    assertThat(primary(word, "document.page_count")).isEqualTo(MetadataValue.of(3));
    assertThat(primary(word, "document.producer"))
        .isEqualTo(MetadataValue.of("Microsoft Office Word"));
    assertThat(raw(word, "OOXML:App", "Words")).isEqualTo(MetadataValue.of(250));
    assertThat(raw(word, "OOXML:App", "ScaleCrop")).isEqualTo(MetadataValue.of(false));
    assertThat(raw(word, "OOXML:App", "AppVersion")).isEqualTo(MetadataValue.of("16.0000"));

    MetadataResult excel = analyze(OfficeFixtures.xlsx(), "a.xlsx");
    assertThat(raw(excel, "OOXML:App", "TitlesOfParts"))
        .isEqualTo(new ListValue(List.of(MetadataValue.of("Vendas"), MetadataValue.of("Custos"))));

    MetadataResult slides = analyze(OfficeFixtures.pptx(), "a.pptx");
    assertThat(primary(slides, "document.page_count")).isEqualTo(MetadataValue.of(12));
    assertThat(raw(slides, "OOXML:App", "HiddenSlides")).isEqualTo(MetadataValue.of(1));
  }

  @Test
  void readsTypedCustomProperties() {
    MetadataResult result = analyze(OfficeFixtures.docx(), "a.docx");

    assertThat(raw(result, "OOXML:Custom", "Projeto")).isEqualTo(MetadataValue.of("MetaCore"));
    assertThat(raw(result, "OOXML:Custom", "Revisão")).isEqualTo(MetadataValue.of(42));
    assertThat(raw(result, "OOXML:Custom", "Custo"))
        .isEqualTo(MetadataValue.of(new BigDecimal("1234.5")));
    assertThat(raw(result, "OOXML:Custom", "Aprovado")).isEqualTo(MetadataValue.of(true));
    assertThat(raw(result, "OOXML:Custom", "Prazo")).isInstanceOf(OffsetDateTimeValue.class);
  }

  @Test
  void reportsThePackageTypeAndMacros() {
    MetadataResult docx = analyze(OfficeFixtures.docx(), "a.docx");
    assertThat(raw(docx, "OOXML", "Content Type"))
        .isEqualTo(MetadataValue.of(OfficeFixtures.DOCX_MAIN));
    assertThat(raw(docx, "OOXML", "Macros")).isEqualTo(MetadataValue.of(false));
    assertThat(raw(docx, "ZIP", "Entries")).isEqualTo(MetadataValue.of(6));

    MetadataResult docm = analyze(OfficeFixtures.docm(), "a.docm");
    assertThat(docm.format()).hasValueSatisfying(f -> assertThat(f.id()).isEqualTo("docx"));
    assertThat(docm.warnings()).isEmpty();
    assertThat(raw(docm, "OOXML", "Macros")).isEqualTo(MetadataValue.of(true));
  }

  @Test
  void readsLibreOfficeFiles() {
    for (String name : List.of("metadados.docx", "metadados.xlsx", "metadados.pptx")) {
      MetadataResult result = analyze(OfficeFixtures.libreOffice(name), name);

      assertThat(result.status()).as(name).isEqualTo(ExtractionStatus.SUCCESS);
      assertThat(primary(result, "document.title")).isEqualTo(MetadataValue.of("Relatório anual"));
      assertThat(primary(result, "document.author")).isEqualTo(MetadataValue.of("Genosha"));
      assertThat(primary(result, "document.last_modified_by"))
          .isEqualTo(MetadataValue.of("Revisor"));
      assertThat(primary(result, "document.created")).isInstanceOf(OffsetDateTimeValue.class);
      assertThat(primary(result, "document.producer"))
          .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(TextValue.class))
          .extracting(TextValue::value)
          .asString()
          .startsWith("LibreOffice/");
      assertThat(raw(result, "OOXML:Custom", "Projeto")).isEqualTo(MetadataValue.of("MetaCore"));
      assertThat(raw(result, "OOXML:Custom", "Aprovado")).isInstanceOf(BooleanValue.class);
      assertThat(raw(result, "OOXML:Custom", "Versão")).isInstanceOf(DecimalValue.class);
    }
  }

  @Test
  void readsArchivesWithDataDescriptors() {
    MetadataResult result = analyze(OfficeFixtures.docxWithDescriptors(), "a.docx");

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(primary(result, "document.title")).isEqualTo(MetadataValue.of("Relatório anual"));
  }

  @Test
  void describesPlainZipFiles() {
    MetadataResult result = analyze(OfficeFixtures.plainZip(), "backup.zip");

    assertThat(result.format()).hasValueSatisfying(f -> assertThat(f.id()).isEqualTo("zip"));
    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(raw(result, "ZIP", "Entries")).isEqualTo(MetadataValue.of(2));
    assertThat(raw(result, "ZIP", "Uncompressed Size")).isEqualTo(MetadataValue.of(9));
    assertThat(raw(result, "ZIP", "Comment")).isEqualTo(MetadataValue.of("Backup de julho"));
    assertThat(result.findRaw("OOXML", "Macros")).isEmpty();
  }

  @Test
  void readsTruncatedFilesThroughTheLocalHeaders() {
    byte[] docx = OfficeFixtures.docx();
    int centralDirectory = indexOf(docx, new byte[] {'P', 'K', 1, 2});

    MetadataResult result = analyze(OfficeFixtures.truncated(docx, centralDirectory), "a.docx");

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings()).extracting(Warning::code).contains(WarningCode.TRUNCATED);
    assertThat(primary(result, "document.title")).isEqualTo(MetadataValue.of("Relatório anual"));
    assertThat(primary(result, "document.page_count")).isEqualTo(MetadataValue.of(3));
    assertThat(result.findRaw("ZIP", "Entries")).isEmpty();
  }

  @Test
  void reportsAPackageWithoutContentTypes() {
    byte[] broken =
        new ZipBuilder()
            .add("_rels/.rels", OfficeFixtures.rootRelationships(true, false, false))
            .add("docProps/core.xml", OfficeFixtures.CORE)
            .add("word/document.xml", OfficeFixtures.DOCUMENT)
            .build();

    MetadataResult result = analyze(broken, "a.docx");

    assertThat(result.format()).hasValueSatisfying(f -> assertThat(f.id()).isEqualTo("docx"));
    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.CORRUPTED_SEGMENT);
  }

  @Test
  void keepsGoingWhenAPartIsBroken() {
    byte[] docx =
        OfficeFixtures.office(
                "word/document.xml",
                OfficeFixtures.DOCX_MAIN,
                OfficeFixtures.core("<dc:title>Sem fim"),
                OfficeFixtures.WORD_APP,
                null)
            .build();

    MetadataResult result = analyze(docx, "a.docx");

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.CORRUPTED_SEGMENT);
              assertThat(w.location()).contains("docProps/core.xml");
            });
    assertThat(primary(result, "document.page_count")).isEqualTo(MetadataValue.of(3));
  }

  @Test
  void keepsUnparseableDatesAsRawText() {
    byte[] docx =
        OfficeFixtures.office(
                "word/document.xml",
                OfficeFixtures.DOCX_MAIN,
                OfficeFixtures.core("<dcterms:created>ontem</dcterms:created>"),
                null,
                null)
            .build();

    MetadataResult result = analyze(docx, "a.docx");

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.VALUE_CONVERSION_FAILED);
    assertThat(raw(result, "OOXML:Core", "dcterms:created")).isEqualTo(MetadataValue.of("ontem"));
  }

  @Test
  void refusesZip64() {
    MetadataResult result =
        analyze(new ZipBuilder().add("a.txt", "x").zip64Marker().build(), "a.zip");

    assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
    assertThat(result.warnings())
        .singleElement()
        .satisfies(w -> assertThat(w.message()).contains("ZIP64"));
  }

  @Test
  void reportsEncryptedEntries() {
    byte[] zip = new ZipBuilder().encrypted("secret.txt", "x").build();

    MetadataResult result = analyze(zip, "a.zip");

    assertThat(raw(result, "ZIP", "Encrypted Entries")).isEqualTo(MetadataValue.of(true));
    assertThat(raw(result, "ZIP", "Entries")).isInstanceOf(IntegerValue.class);
  }

  static int indexOf(byte[] data, byte[] pattern) {
    outer:
    for (int i = 0; i + pattern.length <= data.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) {
          continue outer;
        }
      }
      return i;
    }
    throw new AssertionError("pattern not found");
  }
}
