package br.com.genosha.metacore.metadata;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Valor tipado de um metadado. */
public sealed interface MetadataValue
    permits TextValue,
        IntegerValue,
        DecimalValue,
        RationalValue,
        BooleanValue,
        LocalDateTimeValue,
        OffsetDateTimeValue,
        BinaryValue,
        ListValue {

  /** Tipo lógico do valor. */
  ValueType type();

  static TextValue of(String value) {
    return new TextValue(value);
  }

  static IntegerValue of(long value) {
    return new IntegerValue(value);
  }

  static DecimalValue of(BigDecimal value) {
    return new DecimalValue(value);
  }

  static BooleanValue of(boolean value) {
    return new BooleanValue(value);
  }

  static LocalDateTimeValue of(LocalDateTime value) {
    return new LocalDateTimeValue(value);
  }

  static OffsetDateTimeValue of(OffsetDateTime value) {
    return new OffsetDateTimeValue(value);
  }

  static RationalValue rational(long numerator, long denominator) {
    return new RationalValue(numerator, denominator);
  }
}
