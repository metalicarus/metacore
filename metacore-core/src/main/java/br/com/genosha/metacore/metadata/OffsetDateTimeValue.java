package br.com.genosha.metacore.metadata;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Data e hora com deslocamento de fuso conhecido. */
public record OffsetDateTimeValue(OffsetDateTime value) implements MetadataValue {

  public OffsetDateTimeValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.OFFSET_DATE_TIME;
  }

  /** O próprio valor, como {@code 2026-07-14T16:32:05-03:00}. */
  @Override
  public String toString() {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
  }
}
