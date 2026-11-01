package br.com.genosha.metacore.metadata;

import java.time.OffsetDateTime;
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
}
