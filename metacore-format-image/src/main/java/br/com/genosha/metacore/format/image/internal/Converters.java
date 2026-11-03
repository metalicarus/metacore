package br.com.genosha.metacore.format.image.internal;

import br.com.genosha.metacore.metadata.MetadataValue;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.StringValue;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conversões de campos para os tipos das chaves normalizadas. */
final class Converters {

  /** Converte o campo {@code tagType} de um diretório. Vazio quando a conversão falha. */
  @FunctionalInterface
  interface TagConverter {
    Optional<MetadataValue> convert(Directory directory, int tagType);
  }

  private static final DateTimeFormatter EXIF_DATE =
      DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern FRACTION = Pattern.compile("\\s*(-?\\d+)\\s*/\\s*(\\d+)\\s*");

  private Converters() {}

  static final TagConverter INTEGER = (directory, tag) -> integer(directory.getObject(tag));

  static final TagConverter TEXT = (directory, tag) -> text(directory.getObject(tag));

  /** Descrição legível do metadata-extractor, como {@code True Color with Alpha}. */
  static final TagConverter DESCRIPTION = (directory, tag) -> text(directory.getDescription(tag));

  static final TagConverter RATIONAL =
      (directory, tag) ->
          directory.getObject(tag) instanceof Rational r ? RawValues.rational(r) : Optional.empty();

  /**
   * Data EXIF ({@code 2026:07:14 16:32:05}). Se o campo de fuso indicado ({@code OffsetTime*}) for
   * válido, o resultado tem fuso; senão, fica sem fuso.
   */
  static TagConverter exifDate(int offsetTag) {
    return (directory, tag) -> {
      Optional<String> text = string(directory.getObject(tag));
      if (text.isEmpty()) {
        return Optional.empty();
      }
      LocalDateTime local;
      try {
        local = LocalDateTime.parse(text.get(), EXIF_DATE);
      } catch (DateTimeException e) {
        return Optional.empty();
      }
      Optional<ZoneOffset> offset =
          string(directory.getObject(offsetTag)).flatMap(Converters::offset);
      return Optional.of(
          offset
              .<MetadataValue>map(o -> MetadataValue.of(OffsetDateTime.of(local, o)))
              .orElseGet(() -> MetadataValue.of(local)));
    };
  }

  static final Function<String, Optional<MetadataValue>> XMP_TEXT = Converters::text;

  static final Function<String, Optional<MetadataValue>> XMP_INTEGER = Converters::integer;

  static final Function<String, Optional<MetadataValue>> XMP_RATIONAL =
      value -> {
        Matcher m = FRACTION.matcher(value);
        if (!m.matches()) {
          return Optional.empty();
        }
        try {
          long denominator = Long.parseLong(m.group(2));
          return denominator == 0
              ? Optional.empty()
              : Optional.of(MetadataValue.rational(Long.parseLong(m.group(1)), denominator));
        } catch (NumberFormatException e) {
          return Optional.empty();
        }
      };

  /** Data XMP (ISO 8601), com ou sem fuso. Datas sem hora não são convertidas. */
  static final Function<String, Optional<MetadataValue>> XMP_DATE =
      value -> {
        String trimmed = value.trim();
        try {
          return Optional.of(MetadataValue.of(OffsetDateTime.parse(trimmed)));
        } catch (DateTimeException e) {
          // tenta sem fuso
        }
        try {
          return Optional.of(MetadataValue.of(LocalDateTime.parse(trimmed)));
        } catch (DateTimeException e) {
          return Optional.empty();
        }
      };

  private static Optional<MetadataValue> integer(Object raw) {
    return switch (raw) {
      case Byte b -> Optional.of(MetadataValue.of(b.longValue()));
      case Short s -> Optional.of(MetadataValue.of(s.longValue()));
      case Integer i -> Optional.of(MetadataValue.of(i.longValue()));
      case Long l -> Optional.of(MetadataValue.of(l.longValue()));
      case null -> Optional.empty();
      default -> {
        Optional<String> text = string(raw);
        try {
          yield text.map(t -> MetadataValue.of(Long.parseLong(t)));
        } catch (NumberFormatException e) {
          yield Optional.empty();
        }
      }
    };
  }

  private static Optional<MetadataValue> text(Object raw) {
    return string(raw).map(MetadataValue::of);
  }

  /** Texto sem espaços nas pontas; vazio para nulo, texto vazio ou objetos que não são texto. */
  private static Optional<String> string(Object raw) {
    String text =
        switch (raw) {
          case String s -> s;
          case StringValue s -> s.toString();
          case null, default -> null;
        };
    if (text == null) {
      return Optional.empty();
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
  }

  private static Optional<ZoneOffset> offset(String text) {
    try {
      return Optional.of(ZoneOffset.of(text));
    } catch (DateTimeException e) {
      return Optional.empty();
    }
  }
}
