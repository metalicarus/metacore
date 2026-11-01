package br.com.genosha.metacore.metadata;

import java.util.Objects;
import java.util.Optional;

/**
 * Um campo extraído, com sua origem.
 *
 * @param key chave normalizada; vazia quando o campo não tem mapeamento
 * @param value valor tipado
 * @param rawName nome original do campo no formato ou na biblioteca subjacente
 * @param namespace grupo de origem, por exemplo {@code EXIF:SubIFD} ou {@code PDF:Info}
 * @param source parser que extraiu o campo
 * @param primary {@code true} no valor principal de uma chave normalizada; sempre {@code false}
 *     quando não há chave
 */
public record Metadata(
    Optional<String> key,
    MetadataValue value,
    String rawName,
    String namespace,
    String source,
    boolean primary) {

  public Metadata {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(rawName, "rawName");
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(source, "source");
    key.ifPresent(
        k -> {
          if (!MetadataKeys.isValid(k)) {
            throw new IllegalArgumentException("Invalid metadata key: " + k);
          }
        });
    if (primary && key.isEmpty()) {
      throw new IllegalArgumentException("Only fields with a normalized key can be primary");
    }
  }
}
