package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.cli.CommonOptions.Output;
import br.com.genosha.metacore.exception.MetaCoreException;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Relata uma entrada que não pôde ser analisada. Em JSON, vira uma linha com {@code error}, para
 * que toda entrada tenha sua linha no NDJSON; em texto, vai para stderr.
 */
final class Failures {

  private Failures() {}

  static void report(
      CommandSpec spec,
      CommonOptions options,
      JsonRenderer json,
      Output out,
      Path file,
      MetaCoreException failure) {
    if (options.output == OutputFormat.JSON) {
      out.writer().println(json.error(file.toString(), failure));
    } else {
      spec.commandLine().getErr().println("metacore: " + file + ": " + failure.getMessage());
      spec.commandLine().getErr().flush();
    }
  }
}
