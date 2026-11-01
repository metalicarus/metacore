package br.com.genosha.metacore.api;

import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.metadata.Warning;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de uma identificação, sem extração de metadados.
 *
 * @param file informações básicas da entrada
 * @param detection detecção vencedora; vazia quando o formato é desconhecido
 * @param warnings avisos da detecção
 */
public record IdentificationResult(
    FileInfo file, Optional<DetectionResult> detection, List<Warning> warnings) {

  public IdentificationResult {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(detection, "detection");
    warnings = List.copyOf(warnings);
  }

  /** Formato identificado, quando houver. */
  public Optional<FileFormat> format() {
    return detection.map(DetectionResult::format);
  }
}
