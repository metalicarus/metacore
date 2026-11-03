package br.com.genosha.metacore.format.pdf.internal;

import br.com.genosha.metacore.metadata.MetadataValue;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datas de PDF ({@code D:20260714163205-03'00'}) e de XMP (ISO 8601). Sem fuso declarado, o
 * resultado fica sem fuso: nenhum fuso é presumido.
 */
public final class PdfDates {

  private static final Pattern PDF_DATE =
      Pattern.compile(
          "(?:D:)?(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?"
              + "(?:(Z)(?:00'?00'?)?|([+-])(\\d{2})'?(?:(\\d{2})'?)?)?");

  private PdfDates() {}

  /** Data no formato do dicionário Info. */
  public static Optional<MetadataValue> parsePdf(String text) {
    Matcher m = PDF_DATE.matcher(text.trim());
    if (!m.matches()) {
      return Optional.empty();
    }
    try {
      LocalDateTime local =
          LocalDateTime.of(
              Integer.parseInt(m.group(1)),
              number(m.group(2), 1),
              number(m.group(3), 1),
              number(m.group(4), 0),
              number(m.group(5), 0),
              number(m.group(6), 0));
      if (m.group(7) != null) {
        return Optional.of(MetadataValue.of(OffsetDateTime.of(local, ZoneOffset.UTC)));
      }
      if (m.group(8) != null) {
        int sign = m.group(8).equals("-") ? -1 : 1;
        ZoneOffset offset =
            ZoneOffset.ofHoursMinutes(
                sign * Integer.parseInt(m.group(9)), sign * number(m.group(10), 0));
        return Optional.of(MetadataValue.of(OffsetDateTime.of(local, offset)));
      }
      return Optional.of(MetadataValue.of(local));
    } catch (DateTimeException e) {
      return Optional.empty();
    }
  }

  /** Data XMP (ISO 8601), com ou sem fuso. Datas sem hora não são convertidas. */
  public static Optional<MetadataValue> parseXmp(String text) {
    String trimmed = text.trim();
    try {
      return Optional.of(MetadataValue.of(OffsetDateTime.parse(trimmed)));
    } catch (DateTimeException e) {
      // tenta sem fuso
    }
    try {
      return Optional.of(MetadataValue.of(LocalDateTime.parse(trimmed)));
    } catch (DateTimeException e) {
      return Optional.empty();
    }
  }

  private static int number(String group, int fallback) {
    return group == null ? fallback : Integer.parseInt(group);
  }
}
