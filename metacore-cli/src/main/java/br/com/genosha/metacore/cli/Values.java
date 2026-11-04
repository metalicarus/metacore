package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.BooleanValue;
import br.com.genosha.metacore.metadata.DecimalValue;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.LocalDateTimeValue;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import br.com.genosha.metacore.metadata.RationalValue;
import br.com.genosha.metacore.metadata.TextValue;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/** Valores como texto para pessoas e para CSV. */
final class Values {

  private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
  private static final DateTimeFormatter OFFSET =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx");
  private static final int PREVIEW = 120;

  private Values() {}

  /** Valor numa linha, legível e sem caracteres de controle, cortado se for longo. */
  static String human(MetadataValue value) {
    String text =
        switch (value) {
          case LocalDateTimeValue l -> LOCAL.format(l.value());
          case OffsetDateTimeValue o -> OFFSET.format(o.value());
          case ListValue l ->
              l.values().stream().map(Values::human).collect(Collectors.joining(", "));
          default -> plain(value);
        };
    String safe = safe(text);
    return safe.length() > PREVIEW ? safe.substring(0, PREVIEW) + "..." : safe;
  }

  /** Valor completo, com datas em ISO 8601, para CSV. */
  static String plain(MetadataValue value) {
    return switch (value) {
      case TextValue t -> t.value();
      case IntegerValue i -> Long.toString(i.value());
      case DecimalValue d -> d.value().toPlainString();
      case RationalValue r -> r.toString();
      case BooleanValue b -> Boolean.toString(b.value());
      case LocalDateTimeValue l -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(l.value());
      case OffsetDateTimeValue o -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(o.value());
      case BinaryValue b -> "<binary, " + b.size() + " bytes>";
      case ListValue l -> l.values().stream().map(Values::plain).collect(Collectors.joining(", "));
    };
  }

  /**
   * Troca caracteres de controle e de direção de texto por escapes visíveis. Metadados vêm de
   * arquivos possivelmente hostis e não podem mexer no terminal.
   */
  static String safe(String text) {
    StringBuilder out = null;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      String replacement =
          switch (c) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> unsafe(c) ? String.format(Locale.ROOT, "\\u%04x", (int) c) : null;
          };
      if (replacement != null && out == null) {
        out = new StringBuilder(text.length() + 16).append(text, 0, i);
      }
      if (out != null) {
        if (replacement != null) {
          out.append(replacement);
        } else {
          out.append(c);
        }
      }
    }
    return out == null ? text : out.toString();
  }

  private static boolean unsafe(char c) {
    return c < 0x20
        || (c >= 0x7f && c <= 0x9f)
        || (c >= 0x200e && c <= 0x200f)
        || (c >= 0x202a && c <= 0x202e)
        || (c >= 0x2066 && c <= 0x2069);
  }

  /** Tamanho em bytes, KiB, MiB ou GiB. */
  static String size(OptionalLong size) {
    if (size.isEmpty()) {
      return "";
    }
    long bytes = size.getAsLong();
    if (bytes < 1024) {
      return bytes + " B";
    }
    String[] units = {"KiB", "MiB", "GiB", "TiB"};
    double value = bytes;
    int unit = -1;
    do {
      value /= 1024;
      unit++;
    } while (value >= 1024 && unit < units.length - 1);
    return String.format(Locale.ROOT, value < 10 ? "%.1f %s" : "%.0f %s", value, units[unit]);
  }

  /** Último segmento de uma chave, como rótulo: {@code exposure_time} vira "Exposure time". */
  static String label(String segment) {
    String spaced = segment.replace('_', ' ');
    return spaced.isEmpty()
        ? spaced
        : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }
}
