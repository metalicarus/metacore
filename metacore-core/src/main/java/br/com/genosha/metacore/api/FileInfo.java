package br.com.genosha.metacore.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Informações básicas da entrada. Nunca inclui o caminho do arquivo, só o nome.
 *
 * @param name nome do arquivo, quando conhecido
 * @param size tamanho em bytes
 * @param extension extensão do nome, em minúsculas e sem ponto
 */
public record FileInfo(Optional<String> name, long size, Optional<String> extension) {

  public FileInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(extension, "extension");
    if (size < 0) {
      throw new IllegalArgumentException("Size must not be negative");
    }
  }
}
