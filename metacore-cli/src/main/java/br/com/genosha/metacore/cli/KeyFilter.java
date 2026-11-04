package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.metadata.Metadata;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Seleção de campos por {@code --key}. Cada padrão é uma chave normalizada em que {@code *}
 * representa qualquer sequência, inclusive com pontos: {@code location.*} ou {@code *.width}. Sem
 * padrões, tudo passa; com padrões, só passam campos com chave normalizada que case com algum.
 */
record KeyFilter(List<Pattern> patterns) {

  static final KeyFilter ALL = new KeyFilter(List.of());

  static KeyFilter of(List<String> patterns) {
    return new KeyFilter(
        patterns.stream()
            .flatMap(p -> List.of(p.split(",")).stream())
            .map(String::strip)
            .filter(p -> !p.isEmpty())
            .map(KeyFilter::compile)
            .toList());
  }

  boolean all() {
    return patterns.isEmpty();
  }

  boolean selects(Metadata metadata) {
    return all() || metadata.key().filter(this::matches).isPresent();
  }

  boolean matches(String key) {
    return all() || patterns.stream().anyMatch(p -> p.matcher(key).matches());
  }

  private static Pattern compile(String glob) {
    StringBuilder regex = new StringBuilder();
    for (String part : glob.split("\\*", -1)) {
      if (!regex.isEmpty()) {
        regex.append(".*");
      }
      regex.append(Pattern.quote(part));
    }
    return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
  }
}
