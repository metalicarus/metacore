package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.BooleanValue;
import br.com.genosha.metacore.metadata.DecimalValue;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.LocalDateTimeValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import br.com.genosha.metacore.metadata.RationalValue;
import br.com.genosha.metacore.metadata.TextValue;
import br.com.genosha.metacore.metadata.Warning;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Saída legível para pessoas. Não é um contrato estável: para automação, use JSON. */
final class TextRenderer {

  /** Nível de detalhe do {@code inspect}. */
  enum Detail {
    QUIET,
    NORMAL,
    VERBOSE
  }

  private static final int VALUE_PREVIEW = 120;

  private TextRenderer() {}

  static void inspect(PrintWriter out, String label, MetadataResult result, Detail detail) {
    if (detail == Detail.QUIET) {
      out.println(
          label
              + ": "
              + result.status()
              + format(result.detection()).map(f -> " (" + f + ")").orElse(""));
      return;
    }
    out.println(label);
    out.println("  Format:   " + describe(result.detection()));
    out.println("  Status:   " + result.status());
    if (!result.metadata().isEmpty()) {
      out.println("  Metadata:");
      if (detail == Detail.VERBOSE) {
        int width = keyWidth(result.metadata());
        for (Metadata m : result.metadata()) {
          out.println(
              "    "
                  + (m.primary() ? "* " : "  ")
                  + pad(m.key().orElse("-"), width)
                  + "  "
                  + value(m.value())
                  + "  ("
                  + m.namespace()
                  + " / "
                  + m.rawName()
                  + ")");
        }
      } else {
        List<Metadata> primary =
            result.metadata().stream()
                .filter(Metadata::primary)
                .sorted(Comparator.comparing(m -> m.key().orElseThrow()))
                .toList();
        int width = keyWidth(primary);
        primary.forEach(
            m -> out.println("    " + pad(m.key().orElseThrow(), width) + "  " + value(m.value())));
        int hidden = result.metadata().size() - primary.size();
        if (hidden > 0) {
          out.println("    (" + hidden + " more fields; use --verbose to show all)");
        }
      }
    }
    warnings(out, result.warnings());
  }

  static void identify(PrintWriter out, String label, IdentificationResult result) {
    out.println(label + ": " + describe(result.detection()));
    result
        .warnings()
        .forEach(w -> out.println("  warning " + w.code() + location(w) + ": " + w.message()));
  }

  private static void warnings(PrintWriter out, List<Warning> warnings) {
    if (warnings.isEmpty()) {
      return;
    }
    out.println("  Warnings:");
    warnings.forEach(w -> out.println("    " + w.code() + location(w) + ": " + w.message()));
  }

  private static String location(Warning warning) {
    return warning.location().map(l -> " [" + l + "]").orElse("");
  }

  private static Optional<String> format(Optional<DetectionResult> detection) {
    return detection.map(d -> d.format().displayName());
  }

  private static String describe(Optional<DetectionResult> detection) {
    if (detection.isEmpty()) {
      return "unknown format";
    }
    DetectionResult d = detection.get();
    String evidence = d.evidence().stream().map(Enum::name).collect(Collectors.joining(", "));
    return d.format().displayName()
        + " ("
        + d.format().mimeType()
        + "), confidence "
        + d.confidence()
        + ", evidence "
        + evidence;
  }

  private static String value(MetadataValue value) {
    String text =
        switch (value) {
          case TextValue t -> t.value();
          case IntegerValue i -> Long.toString(i.value());
          case DecimalValue d -> d.value().toPlainString();
          case RationalValue r -> r.toString();
          case BooleanValue b -> Boolean.toString(b.value());
          case LocalDateTimeValue l -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(l.value());
          case OffsetDateTimeValue o -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(o.value());
          case BinaryValue b -> "<binary, " + b.size() + " bytes>";
          case ListValue l ->
              l.values().stream().map(TextRenderer::value).collect(Collectors.joining(", "));
        };
    String singleLine = text.replace("\r", "\\r").replace("\n", "\\n");
    return singleLine.length() > VALUE_PREVIEW
        ? singleLine.substring(0, VALUE_PREVIEW) + "..."
        : singleLine;
  }

  private static int keyWidth(List<Metadata> metadata) {
    return metadata.stream().mapToInt(m -> m.key().orElse("-").length()).max().orElse(0);
  }

  private static String pad(String text, int width) {
    return text + " ".repeat(Math.max(0, width - text.length()));
  }
}
