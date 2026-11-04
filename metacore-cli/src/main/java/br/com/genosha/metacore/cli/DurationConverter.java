package br.com.genosha.metacore.cli;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converte durações como {@code 500ms}, {@code 30s}, {@code 2m} ou {@code 30} (segundos). */
final class DurationConverter implements ITypeConverter<Duration> {

  private static final Pattern DURATION = Pattern.compile("(\\d{1,9})\\s*(MS|S|M|H)?");

  @Override
  public Duration convert(String value) {
    Matcher m = DURATION.matcher(value.trim().toUpperCase(Locale.ROOT));
    if (!m.matches()) {
      throw new TypeConversionException(
          "Invalid duration '" + value + "' (examples: 500ms, 30s, 2m)");
    }
    long number = Long.parseLong(m.group(1));
    if (number == 0) {
      throw new TypeConversionException("Duration must be positive: " + value);
    }
    String unit = m.group(2) == null ? "S" : m.group(2);
    return switch (unit) {
      case "MS" -> Duration.ofMillis(number);
      case "M" -> Duration.ofMinutes(number);
      case "H" -> Duration.ofHours(number);
      default -> Duration.ofSeconds(number);
    };
  }
}
