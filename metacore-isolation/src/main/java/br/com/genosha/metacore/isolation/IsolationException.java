package br.com.genosha.metacore.isolation;

import br.com.genosha.metacore.exception.MetaCoreException;
import java.io.Serial;

/**
 * O worker não pôde ser usado: o processo não subiu, não respondeu a tempo ou não falou o
 * protocolo. É um problema de ambiente, não do conteúdo analisado (ADR 0016).
 */
public class IsolationException extends MetaCoreException {

  @Serial private static final long serialVersionUID = 1L;

  public IsolationException(String message) {
    super(message);
  }

  public IsolationException(String message, Throwable cause) {
    super(message, cause);
  }
}
