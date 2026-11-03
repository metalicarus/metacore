package br.com.genosha.metacore.format.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.pdf.internal.PdfDates;
import br.com.genosha.metacore.metadata.MetadataValue;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PdfDatesTest {

  private static final LocalDateTime MOMENT = LocalDateTime.of(2026, 7, 14, 16, 32, 5);

  @Test
  void parsesPdfDatesWithOffset() {
    assertThat(PdfDates.parsePdf("D:20260714163205-03'00'"))
        .contains(MetadataValue.of(OffsetDateTime.of(MOMENT, ZoneOffset.ofHours(-3))));
    assertThat(PdfDates.parsePdf("D:20260714163205+05'30"))
        .contains(MetadataValue.of(OffsetDateTime.of(MOMENT, ZoneOffset.ofHoursMinutes(5, 30))));
    assertThat(PdfDates.parsePdf("D:20260714163205Z"))
        .contains(MetadataValue.of(OffsetDateTime.of(MOMENT, ZoneOffset.UTC)));
    assertThat(PdfDates.parsePdf("D:20260714163205Z00'00'"))
        .contains(MetadataValue.of(OffsetDateTime.of(MOMENT, ZoneOffset.UTC)));
  }

  @Test
  void keepsDatesWithoutOffsetLocal() {
    assertThat(PdfDates.parsePdf("D:20260714163205")).contains(MetadataValue.of(MOMENT));
    assertThat(PdfDates.parsePdf("20260714163205")).contains(MetadataValue.of(MOMENT));
  }

  @Test
  void fillsMissingTrailingFields() {
    assertThat(PdfDates.parsePdf("D:2026"))
        .contains(MetadataValue.of(LocalDateTime.of(2026, 1, 1, 0, 0)));
    assertThat(PdfDates.parsePdf("D:202607"))
        .contains(MetadataValue.of(LocalDateTime.of(2026, 7, 1, 0, 0)));
  }

  @Test
  void rejectsInvalidDates() {
    assertThat(PdfDates.parsePdf("yesterday")).isEmpty();
    assertThat(PdfDates.parsePdf("D:20261340000000")).isEmpty();
    assertThat(PdfDates.parsePdf("D:20260714163205+25'00'")).isEmpty();
  }

  @Test
  void parsesXmpDates() {
    assertThat(PdfDates.parseXmp("2026-07-14T16:32:05-03:00"))
        .contains(MetadataValue.of(OffsetDateTime.of(MOMENT, ZoneOffset.ofHours(-3))));
    assertThat(PdfDates.parseXmp("2026-07-14T16:32:05")).contains(MetadataValue.of(MOMENT));
    assertThat(PdfDates.parseXmp("2026-07-14")).isEmpty();
  }
}
