package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.ExtractionStatus;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** Contagem do que aconteceu com as entradas, para o resumo final. */
final class Tally {

  private final boolean identify;
  private final long start = System.nanoTime();
  private final Map<ExtractionStatus, Integer> statuses = new EnumMap<>(ExtractionStatus.class);
  private int identified;
  private int unknown;
  private int errors;

  /**
   * @param identify se as entradas são só identificadas, sem extração
   */
  Tally(boolean identify) {
    this.identify = identify;
  }

  boolean identify() {
    return identify;
  }

  void add(ExtractionStatus status) {
    statuses.merge(status, 1, Integer::sum);
  }

  void identified(boolean known) {
    if (known) {
      identified++;
    } else {
      unknown++;
    }
  }

  void error() {
    errors++;
  }

  int status(ExtractionStatus status) {
    return statuses.getOrDefault(status, 0);
  }

  int identified() {
    return identified;
  }

  int unknown() {
    return unknown;
  }

  int errors() {
    return errors;
  }

  int total() {
    return statuses.values().stream().mapToInt(Integer::intValue).sum()
        + identified
        + unknown
        + errors;
  }

  Duration elapsed() {
    return Duration.ofNanos(System.nanoTime() - start);
  }
}
