package br.com.genosha.metacore.exception;

import java.io.Serial;

/**
 * Lançada por {@code ParseContext.checkCancelled()} quando a análise deve parar. Parsers não devem
 * capturá-la; o core a converte em resultado {@code PARTIAL} com aviso {@code CANCELLED}.
 */
public class AnalysisCancelledException extends MetaCoreException {

  @Serial private static final long serialVersionUID = 1L;

  public AnalysisCancelledException(String message) {
    super(message);
  }
}
