package br.com.genosha.metacore.api;

import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.Warning;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado imutável de uma análise.
 *
 * @param file informações básicas da entrada
 * @param detection detecção vencedora; vazia quando o formato é desconhecido
 * @param status estado final
 * @param metadata campos extraídos, na ordem em que o parser os emitiu
 * @param warnings avisos da detecção e da extração
 * @param duration duração da análise; varia entre execuções e fica fora do JSON por padrão
 */
public record MetadataResult(
    FileInfo file,
    Optional<DetectionResult> detection,
    ExtractionStatus status,
    List<Metadata> metadata,
    List<Warning> warnings,
    Duration duration) {

  public MetadataResult {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(detection, "detection");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(duration, "duration");
    metadata = List.copyOf(metadata);
    warnings = List.copyOf(warnings);
  }

  /** Formato identificado, quando houver. */
  public Optional<FileFormat> format() {
    return detection.map(DetectionResult::format);
  }

  /** Valor principal da chave normalizada. */
  public Optional<Metadata> find(String key) {
    return metadata.stream()
        .filter(m -> m.primary() && m.key().orElseThrow().equals(key))
        .findFirst();
  }

  /** Todos os valores da chave normalizada, na ordem de emissão. */
  public List<Metadata> findAll(String key) {
    return metadata.stream().filter(m -> m.key().filter(key::equals).isPresent()).toList();
  }

  /** Campos pelo nome original dentro de um namespace, com ou sem chave normalizada. */
  public List<Metadata> findRaw(String namespace, String rawName) {
    return metadata.stream()
        .filter(m -> m.namespace().equals(namespace) && m.rawName().equals(rawName))
        .toList();
  }
}
