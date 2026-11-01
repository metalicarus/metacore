package br.com.genosha.metacore.format.image;

import br.com.genosha.metacore.format.FileFormat;

/** Formatos de imagem declarados por este módulo. */
public final class ImageFormats {

  public static final FileFormat JPEG =
      FileFormat.of("jpeg", "image/jpeg", "JPEG", "jpg", "jpeg", "jpe", "jfif");

  private ImageFormats() {}
}
