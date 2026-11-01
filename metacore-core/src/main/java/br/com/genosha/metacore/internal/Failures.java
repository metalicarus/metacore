package br.com.genosha.metacore.internal;

/** Descrição curta de falhas para mensagens de aviso. */
public final class Failures {

  private Failures() {}

  public static String describe(Throwable failure) {
    String message = failure.getMessage();
    String type = failure.getClass().getSimpleName();
    return message == null || message.isBlank() ? type : type + ": " + message;
  }
}
