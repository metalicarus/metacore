package br.com.genosha.metacore.exception;

import java.io.Serial;

/**
 * Lançada por um parser para sinalizar falha irrecuperável. O core a converte em resultado {@code
 * FAILED}; ela nunca chega a quem chamou a análise.
 */
public class MetadataExtractionException extends MetaCoreException {

  @Serial private static final long serialVersionUID = 1L;

  public MetadataExtractionException(String message) {
    super(message);
  }

  public MetadataExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}
