package br.com.genosha.metacore.cli;

/** Códigos de saída (blueprint, seção 8.3). Com várias entradas, vale o pior. */
final class ExitCodes {

  static final int OK = 0;
  static final int FAILURE = 1;
  static final int USAGE = 2;
  static final int STRICT = 3;

  private ExitCodes() {}

  /** O pior entre dois códigos: falha, depois modo estrito, depois sucesso. */
  static int worst(int a, int b) {
    return severity(a) >= severity(b) ? a : b;
  }

  private static int severity(int code) {
    return switch (code) {
      case FAILURE -> 3;
      case USAGE -> 2;
      case STRICT -> 1;
      default -> 0;
    };
  }
}
