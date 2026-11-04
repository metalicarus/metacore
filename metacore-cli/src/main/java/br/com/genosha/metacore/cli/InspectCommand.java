package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.isolation.IsolatedAnalyzer;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/** {@code metacore inspect}: identifica e extrai os metadados. */
@Command(
    name = "inspect",
    mixinStandardHelpOptions = true,
    customSynopsis = "metacore [inspect] [OPTIONS] PATH...",
    description = {
      "Identify files and extract their metadata.",
      "This is the default command: 'metacore photo.jpg' is the same as"
          + " 'metacore inspect photo.jpg'."
    })
final class InspectCommand extends AnalysisCommand<MetadataResult> {

  @ArgGroup(validate = false, heading = "%nFields:%n", order = 3)
  FieldOptions fields = new FieldOptions();

  @ArgGroup(validate = false, heading = "%nIsolation:%n", order = 5)
  IsolationOptions isolation = new IsolationOptions();

  /** O que mostrar de cada arquivo. */
  static final class FieldOptions {
    @Option(
        names = {"-v", "--verbose"},
        description = "Show every field as found in the file, not only the normalized ones.")
    boolean verbose;

    @Option(
        names = {"-q", "--quiet"},
        description = "Show only the status of each file.")
    boolean quiet;

    @Option(
        names = {"-k", "--key"},
        paramLabel = "KEY",
        description =
            "Show only normalized fields matching KEY, where * matches anything, e.g."
                + " 'location.*' or '*.width' (repeatable or comma-separated).")
    List<String> keys = new ArrayList<>();

    @Option(
        names = "--include-binary",
        description = "Include binary values as base64 in JSON output (up to 64 KiB each).")
    boolean includeBinary;
  }

  /** Análise em processo separado (ADR 0016). */
  static final class IsolationOptions {
    @Option(
        names = "--isolate",
        description =
            "Analyze in a separate worker process, which can be killed on timeout or crash"
                + " without stopping the command.")
    boolean isolate;

    @Option(
        names = "--timeout",
        paramLabel = "DURATION",
        converter = DurationConverter.class,
        description = "Hard limit per file, e.g. 10s or 2m (default: 30s). Implies --isolate.")
    Duration timeout;

    @Option(
        names = "--max-memory",
        paramLabel = "SIZE",
        converter = DataSizeConverter.class,
        description = "Heap of the worker process, e.g. 256M (default: 512M). Implies --isolate.")
    Long maxMemory;
  }

  @Override
  void validate() {
    // O grupo de campos não valida exclusividade: ele existe só para a seção da ajuda.
    if (fields.verbose && fields.quiet) {
      throw new ParameterException(
          spec.commandLine(), "--verbose and --quiet cannot be used together");
    }
  }

  @Override
  Analyzer<MetadataResult> analyzer() {
    Duration timeout = isolation.timeout;
    Long maxMemory = isolation.maxMemory;
    if (!isolation.isolate && timeout == null && maxMemory == null) {
      MetadataAnalyzer analyzer = options.analyzer();
      return input ->
          input.path().isPresent()
              ? analyzer.analyze(input.path().get())
              : analyzer.analyze(System.in, null);
    }
    // ADR 0016: um worker por job.
    IsolatedAnalyzer.Builder builder =
        IsolatedAnalyzer.builder().limits(options.limits()).workers(options.analysis.jobs);
    if (timeout != null) {
      builder.timeout(timeout);
    }
    if (maxMemory != null) {
      builder.maxHeapSize(maxMemory);
    }
    IsolatedAnalyzer analyzer = builder.build();
    return new Analyzer<>() {
      @Override
      public MetadataResult analyze(Input input) {
        return input.path().isPresent()
            ? analyzer.analyze(input.path().get())
            : analyzer.analyze(System.in, null);
      }

      @Override
      public void close() {
        analyzer.close();
      }
    };
  }

  @Override
  boolean identify() {
    return false;
  }

  @Override
  void report(Report report, Input input, MetadataResult result) {
    report.inspected(input, result);
  }

  @Override
  void count(Tally tally, MetadataResult result) {
    tally.add(result.status());
  }

  @Override
  int exitCode(MetadataResult result) {
    if (result.status() == ExtractionStatus.FAILED) {
      return ExitCodes.FAILURE;
    }
    return options.analysis.strict && result.status() != ExtractionStatus.SUCCESS
        ? ExitCodes.STRICT
        : ExitCodes.OK;
  }

  @Override
  Report textReport(PrintWriter out, PrintWriter err, Style style) {
    TextReport.Detail level =
        fields.verbose
            ? TextReport.Detail.VERBOSE
            : fields.quiet ? TextReport.Detail.QUIET : TextReport.Detail.NORMAL;
    return new TextReport(out, err, style, level, keys(), options.output.showPath);
  }

  @Override
  KeyFilter keys() {
    return KeyFilter.of(fields.keys);
  }

  @Override
  boolean includeBinary() {
    return fields.includeBinary;
  }
}
