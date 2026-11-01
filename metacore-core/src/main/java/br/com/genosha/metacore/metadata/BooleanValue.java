package br.com.genosha.metacore.metadata;

/** Valor booleano. */
public record BooleanValue(boolean value) implements MetadataValue {

  @Override
  public ValueType type() {
    return ValueType.BOOLEAN;
  }
}
