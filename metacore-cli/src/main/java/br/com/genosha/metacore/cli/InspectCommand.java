package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.cli.CommonOptions.Output;
import br.com.genosha.metacore.exception.MetaCoreException;
import br.com.genosha.metacore.isolation.IsolatedAnalyzer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** {@code metacore inspect}: identifica e extrai os metadados. */
@Command(
    name = "inspect",
    mixinStandardHelpOptions = true,
    description = "Identify files and extract their metadata.")
final class InspectCommand implements Callable<Integer> {

  @Mixin CommonOptions options;

  @ArgGroup(exclusive = true)
  DetailOptions detail = new DetailOptions();

  @Option(
      names = "--include-binary",
      description = "Include binary values as base64 in JSON output (up to 64 KiB each).")
  boolean includeBinary;

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

  @Spec CommandSpec spec;

  static final class DetailOptions {
    @Option(
        names = {"-v", "--verbose"},
        description = "Show every field, not only the normalized ones (text output).")
    boolean verbose;

    @Option(
        names = {"-q", "--quiet"},
        description = "Show only the status of each file (text output).")
    boolean quiet;
  }

  @Override
  public Integer call() throws Exception {
    JsonRenderer json = new JsonRenderer(includeBinary);
    int exit = ExitCodes.OK;
    try (Engine engine = engine();
        Output out = options.openOutput(spec)) {
      for (Path file : options.files) {
        exit = ExitCodes.worst(exit, inspect(engine, json, out, file));
      }
    }
    return exit;
  }

  /** Análise no próprio processo ou em workers (ADR 0016). */
  private record Engine(Function<Path, MetadataResult> analyzer, Runnable closer)
      implements AutoCloseable {
    @Override
    public void close() {
      closer.run();
    }
  }

  private Engine engine() {
    if (!isolate && timeout == null && maxMemory == null) {
      return new Engine(options.analyzer()::analyze, () -> {});
    }
    IsolatedAnalyzer.Builder builder = IsolatedAnalyzer.builder().limits(options.limits());
    if (timeout != null) {
      builder.timeout(timeout);
    }
    if (maxMemory != null) {
      builder.maxHeapSize(maxMemory);
    }
    IsolatedAnalyzer analyzer = builder.build();
    return new Engine(analyzer::analyze, analyzer::close);
  }

  private int inspect(Engine engine, JsonRenderer json, Output out, Path file) {
    MetadataResult result;
    try {
      result = engine.analyzer().apply(file);
    } catch (MetaCoreException e) {
      Failures.report(spec, options, json, out, file, e);
      return ExitCodes.FAILURE;
    }
    if (options.output == OutputFormat.JSON) {
      Optional<String> path = options.showPath ? Optional.of(file.toString()) : Optional.empty();
      out.writer().println(json.inspect(result, path));
    } else {
      TextRenderer.inspect(out.writer(), options.label(file), result, detail());
    }
    if (result.status() == ExtractionStatus.FAILED) {
      return ExitCodes.FAILURE;
    }
    return options.strict && result.status() != ExtractionStatus.SUCCESS
        ? ExitCodes.STRICT
        : ExitCodes.OK;
  }

  private TextRenderer.Detail detail() {
    if (detail.verbose) {
      return TextRenderer.Detail.VERBOSE;
    }
    return detail.quiet ? TextRenderer.Detail.QUIET : TextRenderer.Detail.NORMAL;
  }
}
