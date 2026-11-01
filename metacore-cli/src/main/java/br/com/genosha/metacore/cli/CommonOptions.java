package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Opções comuns a {@code identify} e {@code inspect}. */
final class CommonOptions {

  @Parameters(arity = "1..*", paramLabel = "FILE", description = "Files to analyze.")
  List<Path> files;

  @Option(
      names = {"-o", "--output"},
      paramLabel = "FORMAT",
      defaultValue = "text",
      description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
  OutputFormat output;

  @Option(
      names = {"-f", "--file"},
      paramLabel = "OUT",
      description = "Write the output to OUT instead of standard output.")
  Path outputFile;

  @Option(
      names = "--max-file-size",
      paramLabel = "SIZE",
      converter = DataSizeConverter.class,
      description = "Reject inputs larger than SIZE, e.g. 200M or 1G (default: 512M).")
  Long maxFileSize;

  @Option(names = "--show-path", description = "Include the path given on the command line.")
  boolean showPath;

  @Option(names = "--strict", description = "Exit with code 3 unless every input ends in SUCCESS.")
  boolean strict;

  MetadataAnalyzer analyzer() {
    AnalysisLimits limits = AnalysisLimits.defaults();
    if (maxFileSize != null) {
      limits = limits.withMaxFileSize(maxFileSize);
    }
    return MetadataAnalyzer.builder().limits(limits).build();
  }

  /** Destino da saída: o arquivo de {@code --file} ou a saída padrão da linha de comando. */
  Output openOutput(CommandSpec spec) throws IOException {
    if (outputFile == null) {
      return new Output(spec.commandLine().getOut(), false);
    }
    BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
    return new Output(new PrintWriter(writer), true);
  }

  /** Rótulo de uma entrada no texto: o caminho informado com {@code --show-path}, senão o nome. */
  String label(Path file) {
    if (showPath) {
      return file.toString();
    }
    Path name = file.getFileName();
    return name == null ? file.toString() : name.toString();
  }

  /** Destino de escrita; fecha só o que abriu. */
  record Output(PrintWriter writer, boolean owned) implements AutoCloseable {
    @Override
    public void close() {
      if (owned) {
        writer.close();
      } else {
        writer.flush();
      }
    }
  }
}
