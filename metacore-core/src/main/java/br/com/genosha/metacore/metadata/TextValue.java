package br.com.genosha.metacore.metadata;

import java.util.Objects;

/** Texto. */
public record TextValue(String value) implements MetadataValue {

  public TextValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.TEXT;
  }
}
