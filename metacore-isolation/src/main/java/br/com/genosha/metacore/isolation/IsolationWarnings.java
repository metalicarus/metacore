package br.com.genosha.metacore.isolation;

import br.com.genosha.metacore.metadata.WarningCode;

/** Códigos de aviso da análise isolada. Os dois tornam o resultado {@code FAILED}. */
public final class IsolationWarnings {

  /** A análise passou do prazo rígido e o worker foi morto. */
  public static final WarningCode TIMEOUT = new WarningCode("TIMEOUT", true);

  /**
   * O worker morreu durante a análise (falta de memória, queda da JVM, {@code System.exit} num
   * parser) ou respondeu fora do protocolo.
   */
  public static final WarningCode WORKER_FAILED = new WarningCode("WORKER_FAILED", true);

  private IsolationWarnings() {}
}
