package br.com.genosha.metacore.metadata;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Fração, como o tempo de exposição {@code 1/125} do EXIF. O valor não é simplificado: {@code
 * 2/250} continua {@code 2/250}, como está no arquivo.
 */
public record RationalValue(long numerator, long denominator) implements MetadataValue {

  public RationalValue {
    if (denominator == 0) {
      throw new IllegalArgumentException("Denominator must not be zero");
    }
  }

  /** Valor da fração com a precisão informada. */
  public BigDecimal toBigDecimal(MathContext context) {
    return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), context);
  }

  @Override
  public ValueType type() {
    return ValueType.RATIONAL;
  }

  @Override
  public String toString() {
    return numerator + "/" + denominator;
  }
}
