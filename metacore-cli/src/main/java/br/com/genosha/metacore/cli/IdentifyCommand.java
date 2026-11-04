package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.io.InputSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import picocli.CommandLine.Command;

/** {@code metacore identify}: só identifica o formato, sem extrair metadados. */
@Command(
    name = "identify",
    mixinStandardHelpOptions = true,
    customSynopsis = "metacore identify [OPTIONS] PATH...",
    description = "Identify the format of files without extracting metadata.")
final class IdentifyCommand extends AnalysisCommand<IdentificationResult> {

  @Override
  Analyzer<IdentificationResult> analyzer() {
    MetadataAnalyzer analyzer = options.analyzer();
    return input ->
        input.path().isPresent()
            ? analyzer.identify(input.path().get())
            : analyzer.identify(standardInput(analyzer.limits().maxFileSize()));
  }

  /** A identificação precisa de uma fonte relida; a entrada padrão é lida para a memória. */
  private static InputSource standardInput(long maxFileSize) {
    InputStream in = System.in;
    try {
      byte[] content = in.readNBytes((int) Math.min(maxFileSize + 1, Integer.MAX_VALUE - 8));
      if (content.length > maxFileSize) {
        throw new InputLimitExceededException(
            "Standard input is larger than the limit of " + maxFileSize + " bytes");
      }
      return InputSource.of(content, null);
    } catch (IOException e) {
      throw new InvalidInputException("Could not read standard input", e);
    }
  }

  @Override
  boolean identify() {
    return true;
  }

  @Override
  void report(Report report, Input input, IdentificationResult result) {
    report.identified(input, result);
  }

  @Override
  void count(Tally tally, IdentificationResult result) {
    tally.identified(result.detection().isPresent());
  }

  @Override
  int exitCode(IdentificationResult result) {
    return options.analysis.strict && result.detection().isEmpty()
        ? ExitCodes.STRICT
        : ExitCodes.OK;
  }

  @Override
  Report textReport(PrintWriter out, PrintWriter err, Style style) {
    return new TextReport(
        out, err, style, TextReport.Detail.NORMAL, KeyFilter.ALL, options.output.showPath);
  }
}
