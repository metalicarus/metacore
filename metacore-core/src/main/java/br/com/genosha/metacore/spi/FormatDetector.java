package br.com.genosha.metacore.spi;

import java.util.Optional;

/**
 * Reconhece formatos a partir do início do conteúdo. Implementações devem ser thread-safe e não
 * podem depender só da extensão quando o formato tem assinatura.
 */
public interface FormatDetector {

  /** Identificador estável do detector. Usado no desempate e em {@code detectorId}. */
  String id();

  /** Prioridade no desempate entre candidatos de mesma confiança; maior vence. */
  default int priority() {
    return 0;
  }

  /** Avalia o conteúdo. Vazio quando o detector não reconhece nada. */
  Optional<DetectionCandidate> detect(DetectionRequest request);
}
