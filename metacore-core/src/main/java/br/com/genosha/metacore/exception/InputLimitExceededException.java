package br.com.genosha.metacore.exception;

import java.io.Serial;

/** Entrada acima de um limite configurado, como {@code maxFileSize}. */
public class InputLimitExceededException extends InvalidInputException {

  @Serial private static final long serialVersionUID = 1L;

  public InputLimitExceededException(String message) {
    super(message);
  }
}
