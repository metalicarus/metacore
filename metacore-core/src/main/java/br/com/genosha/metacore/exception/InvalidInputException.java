package br.com.genosha.metacore.exception;

import java.io.Serial;

/** Entrada inexistente, ilegível ou inválida antes de a análise começar. */
public class InvalidInputException extends MetaCoreException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidInputException(String message) {
    super(message);
  }

  public InvalidInputException(String message, Throwable cause) {
    super(message, cause);
  }
}
