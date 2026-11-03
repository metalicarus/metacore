package br.com.genosha.metacore.format.pdf;

import br.com.genosha.metacore.format.FileFormat;

/** Formatos declarados por este módulo. */
public final class PdfFormats {

  public static final FileFormat PDF = FileFormat.of("pdf", "application/pdf", "PDF", "pdf");

  private PdfFormats() {}
}
