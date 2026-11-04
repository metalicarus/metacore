package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.ExtractionStatus;
import java.io.Console;
import java.nio.charset.StandardCharsets;

/**
 * Cores ANSI e símbolos da saída em texto. Sem cor, o texto sai sem nenhuma sequência de escape;
 * sem UTF-8, os símbolos viram ASCII.
 */
final class Style {

  /** Quando colorir a saída. */
  enum ColorMode {
    AUTO,
    ALWAYS,
    NEVER
  }

  private final boolean ansi;
  private final boolean unicode;

  Style(boolean ansi, boolean unicode) {
    this.ansi = ansi;
    this.unicode = unicode;
  }

  /**
   * Estilo da saída. No modo automático, só há cor quando a saída é o terminal, {@code NO_COLOR}
   * não está definida e {@code TERM} não é {@code dumb}.
   */
  static Style of(ColorMode mode, boolean toStandardOutput) {
    // Arquivos de saída são sempre UTF-8.
    boolean unicode = !toStandardOutput || StandardCharsets.UTF_8.equals(System.out.charset());
    boolean ansi =
        switch (mode) {
          case ALWAYS -> true;
          case NEVER -> false;
          case AUTO -> toStandardOutput && colorTerminal();
        };
    return new Style(ansi, unicode);
  }

  /** Terminal interativo que aceita cores. */
  static boolean colorTerminal() {
    String noColor = System.getenv("NO_COLOR");
    return terminal()
        && (noColor == null || noColor.isEmpty())
        && !"dumb".equals(System.getenv("TERM"));
  }

  /** Indica se a entrada e a saída padrão são um terminal. */
  static boolean terminal() {
    Console console = System.console();
    return console != null && console.isTerminal();
  }

  boolean ansi() {
    return ansi;
  }

  String bold(String text) {
    return sgr("1", text);
  }

  String dim(String text) {
    return sgr("2", text);
  }

  String red(String text) {
    return sgr("31", text);
  }

  String green(String text) {
    return sgr("32", text);
  }

  String yellow(String text) {
    return sgr("33", text);
  }

  String cyan(String text) {
    return sgr("36", text);
  }

  /** Separador entre informações na mesma linha. */
  String dot() {
    return dim(unicode ? " · " : " - ");
  }

  /** Marcador colorido do status de uma análise. */
  String mark(ExtractionStatus status) {
    return switch (status) {
      case SUCCESS -> green(unicode ? "✓" : "+");
      case PARTIAL -> yellow(unicode ? "◐" : "~");
      case UNSUPPORTED, UNKNOWN_FORMAT -> dim("?");
      case FAILED -> failed();
    };
  }

  String failed() {
    return red(unicode ? "✗" : "x");
  }

  String warning() {
    return yellow("!");
  }

  String times() {
    return unicode ? "×" : "x";
  }

  private String sgr(String code, String text) {
    return ansi ? "\u001b[" + code + "m" + text + "\u001b[0m" : text;
  }
}
