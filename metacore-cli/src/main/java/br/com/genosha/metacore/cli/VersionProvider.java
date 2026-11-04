package br.com.genosha.metacore.cli;

import java.util.stream.Stream;
import picocli.CommandLine.IVersionProvider;

/** Versão lida do manifesto do JAR; {@code dev} fora de um JAR empacotado. */
final class VersionProvider implements IVersionProvider {

  @Override
  public String[] getVersion() {
    String version = MetacoreCli.class.getPackage().getImplementationVersion();
    return Stream.concat(
            Stream.of(Banner.markup()),
            Stream.of(
                "metacore " + (version == null ? "dev" : version),
                "Java " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")"))
        .toArray(String[]::new);
  }
}
