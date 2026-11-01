package br.com.genosha.metacore.api;

/** Estado final de uma análise. */
public enum ExtractionStatus {
  /** Formato identificado e parser executado sem perda de dados. */
  SUCCESS,
  /** Parser executado; parte dos metadados não pôde ser lida. */
  PARTIAL,
  /** Formato identificado, sem parser para ele nem para seus supertipos. */
  UNSUPPORTED,
  /** Nenhum detector reconheceu o conteúdo. */
  UNKNOWN_FORMAT,
  /** Formato identificado e parser disponível, mas a extração falhou. */
  FAILED
}
