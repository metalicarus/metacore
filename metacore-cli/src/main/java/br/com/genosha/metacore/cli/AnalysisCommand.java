package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.cli.CommonOptions.Output;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.exception.MetaCoreException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.PatternSyntaxException;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * Fluxo comum a {@code identify} e {@code inspect}: expande as entradas, analisa em paralelo,
 * entrega os resultados em ordem ao relatório e calcula o código de saída.
 *
 * @param <R> o resultado de uma entrada
 */
abstract class AnalysisCommand<R> implements Callable<Integer> {

  @Mixin CommonOptions options;

  @Spec CommandSpec spec;

  /** Quem analisa uma entrada; usado por várias threads ao mesmo tempo. */
  interface Analyzer<R> extends AutoCloseable {
    R analyze(Input input);

    @Override
    default void close() {}
  }

  private record Outcome<R>(Input input, Optional<R> result, Optional<MetaCoreException> failure) {}

  abstract Analyzer<R> analyzer();

  abstract boolean identify();

  abstract void report(Report report, Input input, R result);

  abstract void count(Tally tally, R result);

  abstract int exitCode(R result);

  abstract Report textReport(PrintWriter out, PrintWriter err, Style style);

  KeyFilter keys() {
    return KeyFilter.ALL;
  }

  /** Validação das opções que o picocli não faz sozinho. */
  void validate() {}

  @Override
  public Integer call() throws Exception {
    validate();
    if (options.analysis.jobs < 1 || options.analysis.jobs > 256) {
      throw new ParameterException(spec.commandLine(), "--jobs must be between 1 and 256");
    }
    Inputs.Expansion expansion;
    try {
      expansion =
          Inputs.expand(
              options.files,
              options.input.recursive,
              options.input.hidden,
              options.input.include,
              options.input.exclude);
    } catch (PatternSyntaxException e) {
      throw new ParameterException(
          spec.commandLine(), "Invalid pattern in --include or --exclude: " + e.getPattern());
    }
    PrintWriter err = spec.commandLine().getErr();
    int exit = ExitCodes.OK;
    if (expansion.inputs().isEmpty()) {
      err.println("metacore: no files to analyze");
    } else {
      exit = analyze(expansion, err);
    }
    if (expansion.skippedDirectories() > 0) {
      int skipped = expansion.skippedDirectories();
      err.println(
          "metacore: skipped "
              + skipped
              + (skipped == 1 ? " subdirectory" : " subdirectories")
              + "; use -r to include them");
    }
    err.flush();
    return exit;
  }

  private int analyze(Inputs.Expansion expansion, PrintWriter err) throws Exception {
    Tally tally = new Tally(identify());
    int[] exit = {ExitCodes.OK};
    try (Analyzer<R> analyzer = analyzer();
        Output out = options.openOutput(spec)) {
      Report report = report(out.writer(), err);
      Progress progress =
          new Progress(
              err,
              expansion.inputs().size(),
              options.output.file != null
                  && Style.colorTerminal()
                  && expansion.inputs().size() > 1);
      Batch.run(
          expansion.inputs(),
          options.analysis.jobs,
          input -> outcome(analyzer, input),
          outcome -> {
            progress.next(outcome.input());
            if (outcome.result().isPresent()) {
              R result = outcome.result().get();
              report(report, outcome.input(), result);
              count(tally, result);
              exit[0] = ExitCodes.worst(exit[0], exitCode(result));
            } else {
              report.failed(outcome.input(), outcome.failure().orElseThrow());
              tally.error();
              exit[0] = ExitCodes.FAILURE;
            }
          });
      progress.done();
      report.finish(tally);
    }
    return exit[0];
  }

  private Report report(PrintWriter out, PrintWriter err) {
    return switch (options.output.format) {
      case TEXT ->
          textReport(out, err, Style.of(options.output.color, options.output.file == null));
      case JSON ->
          new JsonReport(out, new JsonRenderer(includeBinary()), keys(), options.output.showPath);
      case CSV -> new CsvReport(out, keys(), options.output.showPath);
    };
  }

  boolean includeBinary() {
    return false;
  }

  private Outcome<R> outcome(Analyzer<R> analyzer, Input input) {
    if (input.problem().isPresent()) {
      return new Outcome<>(
          input, Optional.empty(), Optional.of(new InvalidInputException(input.problem().get())));
    }
    try {
      return new Outcome<>(input, Optional.of(analyzer.analyze(input)), Optional.empty());
    } catch (MetaCoreException e) {
      return new Outcome<>(input, Optional.empty(), Optional.of(e));
    }
  }

  /** Uma linha de progresso no stderr, quando a saída vai para um arquivo e há um terminal. */
  private static final class Progress {
    private final PrintWriter err;
    private final int total;
    private final boolean enabled;
    private int done;

    Progress(PrintWriter err, int total, boolean enabled) {
      this.err = err;
      this.total = total;
      this.enabled = enabled;
    }

    void next(Input input) {
      done++;
      if (enabled) {
        err.print("\r\u001b[2K" + done + "/" + total + "  " + Values.safe(input.label()));
        err.flush();
      }
    }

    void done() {
      if (enabled) {
        err.print("\r\u001b[2K");
        err.flush();
      }
    }
  }
}
