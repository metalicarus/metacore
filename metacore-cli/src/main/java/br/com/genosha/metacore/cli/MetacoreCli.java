package br.com.genosha.metacore.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Ponto de entrada da ferramenta {@code metacore}. */
@Command(
    name = "metacore",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    description = "Identifies file formats and extracts their metadata.",
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
public final class MetacoreCli implements Runnable {

  @Spec CommandSpec spec;

  @Override
  public void run() {
    throw new ParameterException(spec.commandLine(), "Missing command: use identify or inspect");
  }

  public static void main(String[] args) {
    System.exit(commandLine().execute(args));
  }

  /** Linha de comando configurada, usada por {@link #main} e pelos testes. */
  public static CommandLine commandLine() {
    CommandLine commandLine = new CommandLine(new MetacoreCli());
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);
    commandLine.setExecutionExceptionHandler(
        (exception, cmd, parseResult) -> {
          cmd.getErr().println("metacore: error: " + exception.getMessage());
          cmd.getErr().flush();
          return ExitCodes.FAILURE;
        });
    return commandLine;
  }
}
