package br.com.genosha.metacore.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine;

/** Execução da CLI em memória, com saída padrão e de erro capturadas. */
record CliRun(int exitCode, String out, String err) {

  static CliRun run(String... args) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    CommandLine commandLine = MetacoreCli.commandLine();
    commandLine.setOut(new PrintWriter(out, true));
    commandLine.setErr(new PrintWriter(err, true));
    int exitCode = commandLine.execute(args);
    return new CliRun(exitCode, out.toString(), err.toString());
  }

  List<String> outLines() {
    return Arrays.stream(out.split("\\R")).filter(l -> !l.isBlank()).toList();
  }
}
