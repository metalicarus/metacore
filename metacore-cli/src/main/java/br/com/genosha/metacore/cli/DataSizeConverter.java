package br.com.genosha.metacore.cli;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Converte tamanhos como {@code 512}, {@code 64K}, {@code 200M} ou {@code 1G} (base 1024) em bytes.
 */
final class DataSizeConverter implements ITypeConverter<Long> {

  private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*([KMG]?)B?");

  @Override
  public Long convert(String value) {
    Matcher m = SIZE.matcher(value.trim().toUpperCase(Locale.ROOT));
    if (!m.matches()) {
      throw new TypeConversionException(
          "Invalid size '" + value + "' (examples: 512, 64K, 200M, 1G)");
    }
    long number;
    try {
      number = Long.parseLong(m.group(1));
    } catch (NumberFormatException e) {
      throw new TypeConversionException("Size too large: " + value);
    }
    int shift =
        switch (m.group(2)) {
          case "K" -> 10;
          case "M" -> 20;
          case "G" -> 30;
          default -> 0;
        };
    if (number <= 0 || number > (Long.MAX_VALUE >> shift)) {
      throw new TypeConversionException("Size out of range: " + value);
    }
    return number << shift;
  }
}
