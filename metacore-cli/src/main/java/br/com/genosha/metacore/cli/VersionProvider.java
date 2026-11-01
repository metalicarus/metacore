package br.com.genosha.metacore.cli;

import picocli.CommandLine.IVersionProvider;

/** Versão lida do manifesto do JAR; {@code dev} fora de um JAR empacotado. */
final class VersionProvider implements IVersionProvider {

  @Override
  public String[] getVersion() {
    String version = MetacoreCli.class.getPackage().getImplementationVersion();
    return new String[] {
      "metacore " + (version == null ? "dev" : version),
      "Java " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")"
    };
  }
}
