package br.com.genosha.metacore.format;

/** Tipo de evidência que sustenta uma detecção. */
public enum Evidence {
  /** Assinatura binária (magic number). */
  MAGIC(true),
  /** Estrutura interna validada além da assinatura. */
  STRUCTURE(true),
  /** Extensão do nome do arquivo. */
  EXTENSION(false),
  /** Tipo declarado por quem forneceu o conteúdo. */
  DECLARED_TYPE(false);

  private final boolean internal;

  Evidence(boolean internal) {
    this.internal = internal;
  }

  /** Indica se a evidência vem do próprio conteúdo, e não de metadados externos a ele. */
  public boolean isInternal() {
    return internal;
  }
}
