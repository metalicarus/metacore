package br.com.genosha.metacore.format.image.internal;

import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.MetadataValue;
import com.drew.lang.Rational;
import com.drew.metadata.StringValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Converte os objetos guardados pelo metadata-extractor em valores tipados. */
final class RawValues {

  private RawValues() {}

  /** Valor tipado; vazio quando o objeto não tem representação fiel (por exemplo, fração 0/0). */
  static Optional<MetadataValue> convert(Object raw) {
    return switch (raw) {
      case null -> Optional.empty();
      case String s -> Optional.of(MetadataValue.of(s));
      case StringValue s -> Optional.of(MetadataValue.of(s.toString()));
      case Rational r -> rational(r);
      case Byte b -> Optional.of(MetadataValue.of(b.longValue()));
      case Short s -> Optional.of(MetadataValue.of(s.longValue()));
      case Integer i -> Optional.of(MetadataValue.of(i.longValue()));
      case Long l -> Optional.of(MetadataValue.of(l.longValue()));
      case Float f -> decimal(f.doubleValue());
      case Double d -> decimal(d);
      case Boolean b -> Optional.of(MetadataValue.of(b.booleanValue()));
      case byte[] bytes -> Optional.of(BinaryValue.of(bytes));
      case short[] array -> {
        List<Object> items = new ArrayList<>();
        for (short item : array) {
          items.add(item);
        }
        yield list(items);
      }
      case int[] array -> {
        List<Object> items = new ArrayList<>();
        for (int item : array) {
          items.add(item);
        }
        yield list(items);
      }
      case long[] array -> {
        List<Object> items = new ArrayList<>();
        for (long item : array) {
          items.add(item);
        }
        yield list(items);
      }
      case float[] array -> {
        List<Object> items = new ArrayList<>();
        for (float item : array) {
          items.add(item);
        }
        yield list(items);
      }
      case double[] array -> {
        List<Object> items = new ArrayList<>();
        for (double item : array) {
          items.add(item);
        }
        yield list(items);
      }
      case Object[] array -> list(List.of(array));
      default -> Optional.of(MetadataValue.of(raw.toString()));
    };
  }

  /** Fração do metadata-extractor; vazio quando o denominador é zero. */
  static Optional<MetadataValue> rational(Rational rational) {
    if (rational.getDenominator() == 0) {
      return Optional.empty();
    }
    return Optional.of(MetadataValue.rational(rational.getNumerator(), rational.getDenominator()));
  }

  private static Optional<MetadataValue> decimal(double value) {
    if (!Double.isFinite(value)) {
      return Optional.empty();
    }
    return Optional.of(MetadataValue.of(BigDecimal.valueOf(value)));
  }

  private static Optional<MetadataValue> list(List<?> items) {
    List<MetadataValue> values = new ArrayList<>(items.size());
    for (Object item : items) {
      Optional<MetadataValue> value = convert(item);
      if (value.isEmpty()) {
        return Optional.empty();
      }
      values.add(value.get());
    }
    return Optional.of(new ListValue(values));
  }
}
