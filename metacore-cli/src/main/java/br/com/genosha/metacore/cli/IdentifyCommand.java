package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.cli.CommonOptions.Output;
import br.com.genosha.metacore.exception.MetaCoreException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** {@code metacore identify}: só identifica o formato, sem extrair metadados. */
@Command(
    name = "identify",
    mixinStandardHelpOptions = true,
    description = "Identify the format of files without extracting metadata.")
final class IdentifyCommand implements Callable<Integer> {

  @Mixin CommonOptions options;

  @Spec CommandSpec spec;

  @Override
  public Integer call() throws Exception {
    MetadataAnalyzer analyzer = options.analyzer();
    JsonRenderer json = new JsonRenderer(false);
    int exit = ExitCodes.OK;
    try (Output out = options.openOutput(spec)) {
      for (Path file : options.files) {
        exit = ExitCodes.worst(exit, identify(analyzer, json, out, file));
      }
    }
    return exit;
  }

  private int identify(MetadataAnalyzer analyzer, JsonRenderer json, Output out, Path file) {
    IdentificationResult result;
    try {
      result = analyzer.identify(file);
    } catch (MetaCoreException e) {
      Failures.report(spec, options, json, out, file, e);
      return ExitCodes.FAILURE;
    }
    if (options.output == OutputFormat.JSON) {
      Optional<String> path = options.showPath ? Optional.of(file.toString()) : Optional.empty();
      out.writer().println(json.identify(result, path));
    } else {
      TextRenderer.identify(out.writer(), options.label(file), result);
    }
    return options.strict && result.detection().isEmpty() ? ExitCodes.STRICT : ExitCodes.OK;
  }
}
