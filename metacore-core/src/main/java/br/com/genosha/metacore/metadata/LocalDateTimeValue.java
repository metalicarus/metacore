package br.com.genosha.metacore.metadata;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Data e hora sem fuso conhecido. Nenhum fuso é inventado para preencher a lacuna. */
public record LocalDateTimeValue(LocalDateTime value) implements MetadataValue {

  public LocalDateTimeValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.LOCAL_DATE_TIME;
  }

  /** O próprio valor, como {@code 2026-07-14T16:32:05}. */
  @Override
  public String toString() {
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
  }
}
