package br.com.genosha.metacore.format;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado de uma detecção.
 *
 * @param format formato identificado
 * @param confidence confiança da identificação
 * @param evidence evidências que sustentam a identificação, nunca vazio
 * @param detectorId detector que produziu o resultado
 */
public record DetectionResult(
    FileFormat format, Confidence confidence, Set<Evidence> evidence, String detectorId) {

  public DetectionResult {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(confidence, "confidence");
    Objects.requireNonNull(detectorId, "detectorId");
    if (evidence.isEmpty()) {
      throw new IllegalArgumentException("A detection needs at least one piece of evidence");
    }
    evidence = Collections.unmodifiableSet(EnumSet.copyOf(evidence));
  }
}
