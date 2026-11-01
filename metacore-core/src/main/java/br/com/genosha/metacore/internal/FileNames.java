package br.com.genosha.metacore.internal;

import java.util.Locale;
import java.util.Optional;

/** Utilitários de nome de arquivo. */
public final class FileNames {

  private FileNames() {}

  /** Extensão do nome, em minúsculas e sem ponto. Vazia para nomes sem extensão ou ocultos. */
  public static Optional<String> extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot <= 0 || dot == fileName.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }
}
