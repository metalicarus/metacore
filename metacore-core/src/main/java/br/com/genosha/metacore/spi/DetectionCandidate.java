package br.com.genosha.metacore.spi;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * O que um detector afirma ter reconhecido. O core escolhe entre os candidatos de todos os
 * detectores (ADR 0004 e blueprint, seção 6.3).
 *
 * @param formatId identificador de um formato registrado
 * @param confidence confiança
 * @param evidence evidências, nunca vazio
 */
public record DetectionCandidate(String formatId, Confidence confidence, Set<Evidence> evidence) {

  public DetectionCandidate {
    Objects.requireNonNull(formatId, "formatId");
    Objects.requireNonNull(confidence, "confidence");
    if (evidence.isEmpty()) {
      throw new IllegalArgumentException("A candidate needs at least one piece of evidence");
    }
    evidence = Collections.unmodifiableSet(EnumSet.copyOf(evidence));
  }

  /** Candidato sustentado por uma única evidência. */
  public static DetectionCandidate of(String formatId, Confidence confidence, Evidence evidence) {
    return new DetectionCandidate(formatId, confidence, EnumSet.of(evidence));
  }
}
