package br.com.genosha.metacore.metadata;

/** Número inteiro. */
public record IntegerValue(long value) implements MetadataValue {

  @Override
  public ValueType type() {
    return ValueType.INTEGER;
  }
}
