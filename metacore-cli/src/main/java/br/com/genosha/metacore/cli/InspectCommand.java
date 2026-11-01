package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.cli.CommonOptions.Output;
import br.com.genosha.metacore.exception.MetaCoreException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
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
    MetadataAnalyzer analyzer = options.analyzer();
    JsonRenderer json = new JsonRenderer(includeBinary);
    int exit = ExitCodes.OK;
    try (Output out = options.openOutput(spec)) {
      for (Path file : options.files) {
        exit = ExitCodes.worst(exit, inspect(analyzer, json, out, file));
      }
    }
    return exit;
  }

  private int inspect(MetadataAnalyzer analyzer, JsonRenderer json, Output out, Path file) {
    MetadataResult result;
    try {
      result = analyzer.analyze(file);
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
