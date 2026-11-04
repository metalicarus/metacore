package br.com.genosha.metacore.cli;

import java.io.PrintWriter;

/** O nome em ASCII, mostrado no {@code --help}, no {@code --version} e sem argumentos. */
final class Banner {

  static final String[] LINES = {
    "                  _",
    " _ __ ___    ___ | |_  __ _   ___  ___   _ __  ___",
    "| '_ ` _ \\  / _ \\| __|/ _` | / __|/ _ \\ | '__|/ _ \\",
    "| | | | | ||  __/| |_| (_| || (__| (_) || |  |  __/",
    "|_| |_| |_| \\___| \\__|\\__,_| \\___|\\___/ |_|   \\___|"
  };

  private Banner() {}

  static void print(PrintWriter out, Style style) {
    for (String line : LINES) {
      out.println(style.cyan(line));
    }
  }

  /** O banner na marcação do picocli, para cabeçalhos de ajuda. */
  static String[] markup() {
    String[] lines = new String[LINES.length + 1];
    for (int i = 0; i < LINES.length; i++) {
      lines[i] = "@|cyan " + LINES[i] + "|@";
    }
    lines[LINES.length] = "";
    return lines;
  }
}
