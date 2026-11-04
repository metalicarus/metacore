package br.com.genosha.metacore.api;

import br.com.genosha.metacore.internal.FileNames;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Informações básicas da entrada. Nunca inclui o caminho do arquivo, só o nome.
 *
 * @param name nome do arquivo, quando conhecido
 * @param size tamanho em bytes; vazio para streams que não foram lidos até o fim
 * @param extension extensão do nome, em minúsculas e sem ponto
 */
public record FileInfo(Optional<String> name, OptionalLong size, Optional<String> extension) {

  public FileInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(size, "size");
    Objects.requireNonNull(extension, "extension");
    if (size.isPresent() && size.getAsLong() < 0) {
      throw new IllegalArgumentException("Size must not be negative");
    }
  }

  /** Informações com a extensão calculada a partir do nome. */
  public static FileInfo of(Optional<String> name, OptionalLong size) {
    return new FileInfo(name, size, name.flatMap(FileNames::extension));
  }
}
