package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.MetaCoreException;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.Warning;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saída legível para pessoas: campos agrupados, valores formatados e resumo no fim. Não é um
 * contrato estável; para automação, use JSON ou CSV.
 */
final class TextReport implements Report {

  /** Nível de detalhe do {@code inspect}. */
  enum Detail {
    QUIET,
    NORMAL,
    VERBOSE
  }

  /** Unidades das chaves que têm uma. */
  private static final Map<String, String> UNITS =
      Map.of(
          "image.width", "px",
          "image.height", "px",
          "image.thumbnail.width", "px",
          "image.thumbnail.height", "px",
          "image.camera.exposure_time", "s");

  private final PrintWriter out;
  private final PrintWriter err;
  private final Style style;
  private final Detail detail;
  private final KeyFilter keys;
  private final boolean showPath;

  TextReport(
      PrintWriter out,
      PrintWriter err,
      Style style,
      Detail detail,
      KeyFilter keys,
      boolean showPath) {
    this.out = out;
    this.err = err;
    this.style = style;
    this.detail = detail;
    this.keys = keys;
    this.showPath = showPath;
  }

  @Override
  public void inspected(Input input, MetadataResult result) {
    if (detail == Detail.QUIET) {
      out.println(
          label(input)
              + ": "
              + status(result.status())
              + result.format().map(f -> " (" + f.displayName() + ")").orElse(""));
      return;
    }
    out.println(header(input, result));
    if (detail == Detail.VERBOSE) {
      result.detection().ifPresent(d -> out.println("  " + style.dim("Detected  ") + detection(d)));
    }
    List<Metadata> primary =
        result.metadata().stream().filter(Metadata::primary).filter(keys::selects).toList();
    fields(primary);
    if (detail == Detail.VERBOSE) {
      raw(result.metadata().stream().filter(keys::selects).toList());
    } else if (keys.all() && result.metadata().size() > primary.size()) {
      int hidden = result.metadata().size() - primary.size();
      out.println(
          "  "
              + style.dim(
                  "+ " + hidden + (hidden == 1 ? " more field" : " more fields") + " (--verbose)"));
    }
    warnings(result.warnings());
    out.println();
  }

  @Override
  public void identified(Input input, IdentificationResult result) {
    String line =
        result
            .detection()
            .map(
                d ->
                    style.mark(ExtractionStatus.SUCCESS)
                        + " "
                        + style.bold(label(input))
                        + "  "
                        + detection(d))
            .orElseGet(
                () ->
                    style.mark(ExtractionStatus.UNKNOWN_FORMAT)
                        + " "
                        + style.bold(label(input))
                        + "  "
                        + style.dim("unknown format"));
    out.println(line);
    warnings(result.warnings());
  }

  @Override
  public void failed(Input input, MetaCoreException failure) {
    out.flush();
    err.println("metacore: " + input.given() + ": " + failure.getMessage());
    err.flush();
  }

  @Override
  public void finish(Tally tally) {
    if (detail == Detail.QUIET || tally.total() < 2) {
      return;
    }
    List<String> parts = new ArrayList<>();
    parts.add(style.bold(tally.total() + " files"));
    int failed = tally.status(ExtractionStatus.FAILED) + tally.errors();
    if (tally.identify()) {
      parts.add(style.green(tally.identified() + " identified"));
      count(parts, tally.unknown(), "unknown", Function.identity());
    } else {
      parts.add(style.green(tally.status(ExtractionStatus.SUCCESS) + " ok"));
      count(parts, tally.status(ExtractionStatus.PARTIAL), "partial", style::yellow);
      count(parts, tally.status(ExtractionStatus.UNSUPPORTED), "unsupported", Function.identity());
      count(parts, tally.status(ExtractionStatus.UNKNOWN_FORMAT), "unknown", Function.identity());
    }
    count(parts, failed, "failed", style::red);
    parts.add(style.dim(duration(tally.elapsed())));
    if (tally.identify()) {
      out.println(); // o inspect já deixa uma linha em branco depois de cada arquivo
    }
    out.println(String.join(style.dot(), parts));
  }

  private static void count(
      List<String> parts, int count, String name, Function<String, String> color) {
    if (count > 0) {
      parts.add(color.apply(count + " " + name));
    }
  }

  private String header(Input input, MetadataResult result) {
    StringBuilder line =
        new StringBuilder(style.mark(result.status()))
            .append(' ')
            .append(style.bold(label(input)))
            .append("  ")
            .append(result.format().map(f -> f.displayName()).orElse(style.dim("unknown format")));
    String size = Values.size(result.file().size());
    if (!size.isEmpty()) {
      line.append(style.dot()).append(size);
    }
    switch (result.status()) {
      case PARTIAL -> line.append(style.dot()).append(style.yellow("partial"));
      case UNSUPPORTED -> line.append(style.dot()).append(style.dim("no metadata parser"));
      case FAILED -> line.append(style.dot()).append(style.red("failed"));
      default -> {}
    }
    return line.toString();
  }

  private String detection(DetectionResult d) {
    String evidence =
        d.evidence().stream()
            .map(e -> e.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(", "));
    return d.format().displayName()
        + " ("
        + d.format().mimeType()
        + ")"
        + style.dot()
        + style.dim(d.confidence().name().toLowerCase(Locale.ROOT) + " confidence: " + evidence);
  }

  /** Campos normalizados agrupados pelo prefixo da chave: {@code image.camera.make} em "Camera". */
  private void fields(List<Metadata> metadata) {
    Map<String, List<Metadata>> groups = new LinkedHashMap<>();
    metadata.stream()
        .sorted(Comparator.comparing(m -> m.key().orElseThrow()))
        .forEach(
            m ->
                groups
                    .computeIfAbsent(group(m.key().orElseThrow()), g -> new ArrayList<>())
                    .add(m));
    int width =
        metadata.stream()
            .mapToInt(m -> Values.label(last(m.key().orElseThrow())).length())
            .max()
            .orElse(0);
    groups.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            group -> {
              out.println("  " + style.bold(Values.label(last(group.getKey()))));
              for (Metadata m : group.getValue()) {
                String key = m.key().orElseThrow();
                String unit = UNITS.get(key);
                out.println(
                    "    "
                        + pad(Values.label(last(key)), width)
                        + "  "
                        + Values.human(m.value())
                        + (unit == null ? "" : " " + unit));
              }
            });
  }

  /** Todos os campos como vieram do arquivo, agrupados pelo namespace. */
  private void raw(List<Metadata> metadata) {
    if (metadata.isEmpty()) {
      return;
    }
    out.println("  " + style.bold("Raw fields"));
    int width = metadata.stream().mapToInt(m -> Values.safe(m.rawName()).length()).max().orElse(0);
    Map<String, List<Metadata>> namespaces =
        metadata.stream()
            .collect(
                Collectors.groupingBy(
                    Metadata::namespace, LinkedHashMap::new, Collectors.toList()));
    namespaces.forEach(
        (namespace, entries) -> {
          out.println("    " + style.cyan(Values.safe(namespace)));
          for (Metadata m : entries) {
            out.println(
                "      "
                    + pad(Values.safe(m.rawName()), width)
                    + "  "
                    + Values.human(m.value())
                    + m.key().map(k -> style.dim("  -> " + k)).orElse(""));
          }
        });
  }

  private void warnings(List<Warning> warnings) {
    for (Warning w : warnings) {
      out.println(
          "  "
              + style.warning()
              + " "
              + style.yellow(w.code().value())
              + "  "
              + Values.safe(w.message())
              + w.location().map(l -> style.dim(" [" + Values.safe(l) + "]")).orElse(""));
    }
  }

  private String status(ExtractionStatus status) {
    return switch (status) {
      case SUCCESS -> style.green(status.name());
      case PARTIAL -> style.yellow(status.name());
      case FAILED -> style.red(status.name());
      default -> status.name();
    };
  }

  private String label(Input input) {
    return Values.safe(showPath ? input.given() : input.label());
  }

  private static String group(String key) {
    int dot = key.lastIndexOf('.');
    return dot < 0 ? "other" : key.substring(0, dot);
  }

  private static String last(String key) {
    return key.substring(key.lastIndexOf('.') + 1);
  }

  private static String pad(String text, int width) {
    return text + " ".repeat(Math.max(0, width - text.length()));
  }

  static String duration(Duration elapsed) {
    long millis = elapsed.toMillis();
    if (millis < 1000) {
      return millis + " ms";
    }
    if (millis < 60_000) {
      return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
    return elapsed.toMinutes() + "m " + elapsed.toSecondsPart() + "s";
  }
}
