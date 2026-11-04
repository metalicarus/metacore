package br.com.genosha.metacore.comparison;

import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.comparison.Fixtures.Fixture;
import br.com.genosha.metacore.comparison.Runners.TikaOutcome;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.testkit.ResultRenderer;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Gera os dados brutos da Fase 0 em um diretório: {@code outcomes.md} (H3), {@code dumps/} com a
 * saída completa de cada lado por fixture (H1 e H2) e {@code performance.md} (H4).
 *
 * <p>Uso: {@code java -cp ... ComparisonMain <diretório de saída>}; ou {@code ComparisonMain cold
 * metacore|tika <arquivo>} para medir a primeira análise numa JVM nova.
 */
public final class ComparisonMain {

  private ComparisonMain() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 3 && args[0].equals("cold")) {
      cold(args[1], Path.of(args[2]));
      return;
    }
    Path out = Path.of(args[0]);
    Files.createDirectories(out.resolve("dumps"));
    Path secret = Files.writeString(out.resolve("secret.txt"), "METACORE-SECRET-0f1e");
    List<Fixture> fixtures = Fixtures.all(secret.toUri().toString());

    try (PrintWriter outcomes = writer(out.resolve("outcomes.md"))) {
      outcomes.println("| Fixture | MetaCore | Tika |");
      outcomes.println("|---|---|---|");
      for (Fixture f : fixtures) {
        MetadataResult mc = Runners.metacore(f.content(), f.fileName());
        TikaOutcome tk = Runners.tika(f.content(), f.fileName());
        outcomes.println(
            "| " + f.name() + " | " + metacoreSummary(mc) + " | " + tikaSummary(tk) + " |");
        Files.writeString(
            out.resolve("dumps/" + f.name() + ".metacore.txt"), ResultRenderer.render(mc));
        Files.writeString(out.resolve("dumps/" + f.name() + ".tika.txt"), tikaDump(tk));
        boolean leaked =
            ResultRenderer.render(mc).contains("METACORE-SECRET")
                || tikaDump(tk).contains("METACORE-SECRET");
        if (leaked) {
          outcomes.println("| ^ SECRET LEAKED | | |");
        }
      }
    }

    try (PrintWriter perf = writer(out.resolve("performance.md"))) {
      perf.println(
          "| Entrada | Origem | MetaCore tempo | MetaCore alocação | Tika tempo | Tika alocação |");
      perf.println("|---|---|---|---|---|---|");
      Path files = Files.createDirectories(out.resolve("perf-files"));
      for (Map.Entry<String, Fixture> e : PerfInputs.create().entrySet()) {
        Fixture f = e.getValue();
        Path file = Files.write(files.resolve(f.fileName()), f.content());
        Measure mcStream = measure(() -> Runners.metacore(f.content(), f.fileName()));
        Measure tkStream = measure(() -> Runners.tika(f.content(), f.fileName()));
        Measure mcFile = measure(() -> Runners.metacore(file));
        Measure tkFile = measure(() -> Runners.tika(file));
        perf.printf(
            "| %s | stream | %s | %s | %s | %s |%n",
            e.getKey(), mcStream.time(), mcStream.alloc(), tkStream.time(), tkStream.alloc());
        perf.printf(
            "| %s | arquivo | %s | %s | %s | %s |%n",
            e.getKey(), mcFile.time(), mcFile.alloc(), tkFile.time(), tkFile.alloc());
      }
    }
  }

  private static String metacoreSummary(MetadataResult r) {
    long normalized = r.metadata().stream().filter(Metadata::primary).count();
    String codes =
        r.warnings().stream()
            .map(Warning::code)
            .map(Object::toString)
            .distinct()
            .collect(Collectors.joining(", "));
    return r.status()
        + " — "
        + r.metadata().size()
        + " campos ("
        + normalized
        + " normalizados)"
        + (codes.isEmpty() ? "" : "; avisos: " + codes);
  }

  private static String tikaSummary(TikaOutcome t) {
    String base = t.contentType() + " — " + t.metadata().size() + " chaves";
    long warnings =
        t.metadata().keySet().stream().filter(k -> k.startsWith("X-TIKA:EXCEPTION")).count();
    String warn = warnings == 0 ? "" : "; " + warnings + " chave(s) X-TIKA:EXCEPTION";
    return t.failure() == null
        ? base + warn
        : "**exceção** " + Runners.describe(t.failure()) + " (" + base + ")";
  }

  private static String tikaDump(TikaOutcome t) {
    StringBuilder s = new StringBuilder();
    if (t.failure() != null) {
      s.append("EXCEPTION ").append(Runners.describe(t.failure())).append('\n');
    }
    t.metadata()
        .forEach(
            (k, v) -> {
              String value = Runners.join(v);
              if (value.length() > 200) {
                value = value.substring(0, 200) + "... (" + value.length() + " chars)";
              }
              s.append(k).append(" = ").append(value.replace("\n", "\\n")).append('\n');
            });
    return s.toString();
  }

  record Measure(String time, String alloc) {}

  /** Média de tempo e de alocação por chamada na thread atual, depois de aquecer. */
  private static Measure measure(Supplier<?> work) {
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (System.nanoTime() < deadline) {
      work.get();
    }
    int runs = 0;
    long allocStart = threads.getCurrentThreadAllocatedBytes();
    long start = System.nanoTime();
    long end = start + 5_000_000_000L;
    while (System.nanoTime() < end || runs < 3) {
      work.get();
      runs++;
    }
    double micros = (System.nanoTime() - start) / 1000.0 / runs;
    double bytes = (double) (threads.getCurrentThreadAllocatedBytes() - allocStart) / runs;
    return new Measure(formatTime(micros), formatBytes(bytes));
  }

  private static String formatTime(double micros) {
    return micros >= 1000
        ? String.format("%.1f ms", micros / 1000)
        : String.format("%.0f µs", micros);
  }

  private static String formatBytes(double bytes) {
    if (bytes >= 1024 * 1024) {
      return String.format("%.1f MB", bytes / 1024 / 1024);
    }
    return String.format("%.0f KB", bytes / 1024);
  }

  /** Uma análise numa JVM nova, para medir o custo de inicialização. */
  private static void cold(String library, Path file) throws IOException {
    byte[] content = Files.readAllBytes(file);
    long start = System.nanoTime();
    if (library.equals("metacore")) {
      Runners.metacore(content, file.getFileName().toString());
    } else {
      Runners.tika(content, file.getFileName().toString());
    }
    System.out.printf("%s first-analysis %.0f ms%n", library, (System.nanoTime() - start) / 1e6);
  }

  private static PrintWriter writer(Path path) throws IOException {
    return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
  }
}
