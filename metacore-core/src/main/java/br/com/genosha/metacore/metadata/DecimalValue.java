package br.com.genosha.metacore.metadata;

import java.math.BigDecimal;
import java.util.Objects;

/** Número decimal, por exemplo latitude e longitude em graus decimais. */
public record DecimalValue(BigDecimal value) implements MetadataValue {

  public DecimalValue {
    Objects.requireNonNull(value, "value");
  }

  @Override
  public ValueType type() {
    return ValueType.DECIMAL;
  }

  /** O próprio valor, como {@code -23.5500000}. */
  @Override
  public String toString() {
    return value.toPlainString();
  }
}
