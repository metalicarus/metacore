package br.com.genosha.metacore.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Uma entrada a analisar.
 *
 * @param path o arquivo; vazio para a entrada padrão
 * @param label como a entrada aparece no texto: o nome do arquivo, ou o caminho relativo à pasta
 *     informada
 * @param problem motivo pelo qual a entrada não pode ser lida, achado ao percorrer uma pasta
 */
record Input(Optional<Path> path, String label, Optional<String> problem) {

  static final String STANDARD_INPUT = "-";

  static Input file(Path path, String label) {
    return new Input(Optional.of(path), label, Optional.empty());
  }

  static Input standardInput() {
    return new Input(Optional.empty(), "<stdin>", Optional.empty());
  }

  static Input unreadable(Path path, String label, String problem) {
    return new Input(Optional.of(path), label, Optional.of(problem));
  }

  /** A entrada como foi informada: o caminho, ou {@code -} para a entrada padrão. */
  String given() {
    return path.map(Path::toString).orElse(STANDARD_INPUT);
  }
}
