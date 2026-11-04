package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Opções comuns a {@code identify} e {@code inspect}. */
final class CommonOptions {

  @Parameters(
      arity = "1..*",
      paramLabel = "PATH",
      description = "Files or directories to analyze; - reads standard input.")
  List<Path> files;

  @ArgGroup(validate = false, heading = "%nInput:%n", order = 1)
  InputOptions input = new InputOptions();

  @ArgGroup(validate = false, heading = "%nOutput:%n", order = 2)
  OutputOptions output = new OutputOptions();

  @ArgGroup(validate = false, heading = "%nAnalysis:%n", order = 4)
  AnalysisOptions analysis = new AnalysisOptions();

  /** Como as pastas são percorridas. */
  static final class InputOptions {
    @Option(
        names = {"-r", "--recursive"},
        description = "Also analyze the subdirectories of directories.")
    boolean recursive;

    @Option(
        names = "--include",
        paramLabel = "GLOB",
        description =
            "In directories, analyze only files matching GLOB, e.g. '*.jpg' (repeatable,"
                + " case-insensitive).")
    List<String> include = new ArrayList<>();

    @Option(
        names = "--exclude",
        paramLabel = "GLOB",
        description = "In directories, skip files matching GLOB (repeatable, case-insensitive).")
    List<String> exclude = new ArrayList<>();

    @Option(names = "--hidden", description = "In directories, include hidden files and folders.")
    boolean hidden;
  }

  /** Formato e destino da saída. Os valores iniciais valem quando nenhuma opção do grupo é dada. */
  static final class OutputOptions {
    @Option(
        names = {"-o", "--output"},
        paramLabel = "FORMAT",
        defaultValue = "text",
        description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    OutputFormat format = OutputFormat.TEXT;

    @Option(
        names = {"-f", "--file"},
        paramLabel = "OUT",
        description = "Write the output to OUT instead of standard output.")
    Path file;

    @Option(names = "--show-path", description = "Include the path given on the command line.")
    boolean showPath;

    @Option(
        names = "--color",
        paramLabel = "WHEN",
        defaultValue = "auto",
        description =
            "Color the text output: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE};"
                + " auto respects NO_COLOR).")
    Style.ColorMode color = Style.ColorMode.AUTO;
  }

  /** Execução e limites. */
  static final class AnalysisOptions {
    @Option(
        names = {"-j", "--jobs"},
        paramLabel = "N",
        defaultValue = "1",
        description = "Analyze up to N files at the same time (default: ${DEFAULT-VALUE}).")
    int jobs = 1;

    @Option(
        names = "--max-file-size",
        paramLabel = "SIZE",
        converter = DataSizeConverter.class,
        description = "Reject inputs larger than SIZE, e.g. 200M or 1G (default: 512M).")
    Long maxFileSize;

    @Option(
        names = "--strict",
        description = "Exit with code 3 unless every input ends in SUCCESS.")
    boolean strict;
  }

  MetadataAnalyzer analyzer() {
    return MetadataAnalyzer.builder().limits(limits()).build();
  }

  AnalysisLimits limits() {
    AnalysisLimits limits = AnalysisLimits.defaults();
    return analysis.maxFileSize == null ? limits : limits.withMaxFileSize(analysis.maxFileSize);
  }

  /** Destino da saída: o arquivo de {@code --file} ou a saída padrão da linha de comando. */
  Output openOutput(CommandSpec spec) throws IOException {
    if (output.file == null) {
      return new Output(spec.commandLine().getOut(), false);
    }
    BufferedWriter writer = Files.newBufferedWriter(output.file, StandardCharsets.UTF_8);
    return new Output(new PrintWriter(writer), true);
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
