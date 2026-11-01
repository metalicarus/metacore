package br.com.genosha.metacore.exception;

import java.io.Serial;

/** Exceção-base do MetaCore. */
public class MetaCoreException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MetaCoreException(String message) {
    super(message);
  }

  public MetaCoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
