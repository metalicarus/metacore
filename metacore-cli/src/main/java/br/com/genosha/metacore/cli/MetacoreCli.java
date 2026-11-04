package br.com.genosha.metacore.cli;

import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine.UnmatchedArgumentException;

/** Ponto de entrada da ferramenta {@code metacore}. */
@Command(
    name = "metacore",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    description = {
      "Identifies file formats and extracts their metadata.",
      "Without a command, analyzes the given files as 'inspect' does."
    },
    subcommands = {IdentifyCommand.class, InspectCommand.class, CommandLine.HelpCommand.class},
    exitCodeListHeading = "%nExit codes:%n",
    exitCodeList = {
      "0:All inputs were analyzed (including PARTIAL, UNSUPPORTED and UNKNOWN_FORMAT).",
      "1:At least one input failed or could not be read.",
      "2:Invalid usage.",
      "3:With --strict, at least one input did not end in SUCCESS."
    },
    footer = {
      "",
      "Output may contain personal data stored in the files (GPS coordinates, names,",
      "serial numbers). Paths are omitted unless --show-path is given."
    })
public final class MetacoreCli implements Callable<Integer> {

  private static final Set<String> ROOT_OPTIONS = Set.of("-h", "--help", "-V", "--version");

  @Spec CommandSpec spec;

  /** Sem argumentos: o banner e os usos mais comuns. */
  @Override
  public Integer call() {
    PrintWriter out = spec.commandLine().getOut();
    Style style = new Style(spec.commandLine().getColorScheme().ansi().enabled(), true);
    Banner.print(out, style);
    out.println();
    out.println("  Identifies file formats and extracts their metadata.");
    out.println();
    example(out, style, "metacore photo.jpg", "inspect a file");
    example(out, style, "metacore ./photos -r", "inspect a folder and its subfolders");
    example(out, style, "metacore identify *", "only detect the formats");
    example(out, style, "metacore ./photos -r -o csv -f photos.csv", "export a spreadsheet");
    example(out, style, "metacore doc.pdf -o json", "machine-readable output");
    example(out, style, "cat doc.pdf | metacore -", "read standard input");
    out.println();
    out.println(
        "  Run "
            + style.bold("metacore --help")
            + " for the commands and "
            + style.bold("metacore inspect --help")
            + " for every option.");
    out.flush();
    return ExitCodes.OK;
  }

  private static void example(PrintWriter out, Style style, String command, String meaning) {
    out.println("  " + style.cyan(String.format("%-44s", command)) + style.dim(meaning));
  }

  public static void main(String[] args) {
    System.exit(execute(commandLine(), args));
  }

  /** Executa a linha de comando; sem um comando conhecido, os argumentos vão para o inspect. */
  static int execute(CommandLine commandLine, String... args) {
    if (args.length > 0
        && !commandLine.getSubcommands().containsKey(args[0])
        && !ROOT_OPTIONS.contains(args[0])) {
      args = Stream.concat(Stream.of("inspect"), Stream.of(args)).toArray(String[]::new);
    }
    return commandLine.execute(args);
  }

  /** Linha de comando configurada, usada por {@link #main} e pelos testes. */
  public static CommandLine commandLine() {
    CommandLine commandLine = new CommandLine(new MetacoreCli());
    commandLine.addSubcommand("completion", new AutoComplete.GenerateCompletion());
    commandLine.getCommandSpec().usageMessage().header(Banner.markup());
    commandLine.setColorScheme(
        CommandLine.Help.defaultColorScheme(Style.colorTerminal() ? Ansi.ON : Ansi.OFF));
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);
    commandLine.setParameterExceptionHandler(
        (exception, args) -> {
          CommandLine cmd = exception.getCommandLine();
          PrintWriter err = cmd.getErr();
          err.println("metacore: " + exception.getMessage());
          UnmatchedArgumentException.printSuggestions(exception, err);
          err.println("Run '" + cmd.getCommandSpec().qualifiedName() + " --help' for usage.");
          err.flush();
          return ExitCodes.USAGE;
        });
    commandLine.setExecutionExceptionHandler(
        (exception, cmd, parseResult) -> {
          cmd.getErr().println("metacore: error: " + exception.getMessage());
          cmd.getErr().flush();
          return ExitCodes.FAILURE;
        });
    return commandLine;
  }
}
