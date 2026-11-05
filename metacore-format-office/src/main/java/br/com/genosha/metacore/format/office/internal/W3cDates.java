package br.com.genosha.metacore.format.office.internal;

import br.com.genosha.metacore.metadata.MetadataValue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Datas W3CDTF das propriedades OOXML ({@code dcterms:W3CDTF}, {@code vt:filetime}). Com fuso, vira
 * {@code OFFSET_DATE_TIME}; sem fuso ou só com a data, {@code LOCAL_DATE_TIME}.
 */
public final class W3cDates {

  private W3cDates() {}

  public static Optional<MetadataValue> parse(String text) {
    String value = text.strip();
    try {
      return Optional.of(MetadataValue.of(OffsetDateTime.parse(value)));
    } catch (DateTimeParseException e) {
      // tenta sem fuso
    }
    try {
      return Optional.of(MetadataValue.of(LocalDateTime.parse(value)));
    } catch (DateTimeParseException e) {
      // tenta só a data
    }
    try {
      return Optional.of(MetadataValue.of(LocalDate.parse(value).atStartOfDay()));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
