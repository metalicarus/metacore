package br.com.genosha.metacore.spi;

import br.com.genosha.metacore.metadata.MetadataKeys;
import br.com.genosha.metacore.metadata.MetadataValue;
import java.util.Objects;
import java.util.Optional;

/**
 * Campo emitido por um parser. O core acrescenta o {@code source} e decide qual valor de cada chave
 * é o principal, a partir da precedência (ADR 0005).
 *
 * @param key chave normalizada, ou vazio para campos sem mapeamento
 * @param value valor tipado
 * @param rawName nome original do campo
 * @param namespace grupo de origem
 * @param precedence ordem de preferência entre valores da mesma chave; menor vence
 */
public record MetadataEntry(
    Optional<String> key, MetadataValue value, String rawName, String namespace, int precedence) {

  /** Precedência padrão de um campo. */
  public static final int DEFAULT_PRECEDENCE = 100;

  public MetadataEntry {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(rawName, "rawName");
    Objects.requireNonNull(namespace, "namespace");
    key.ifPresent(
        k -> {
          if (!MetadataKeys.isValid(k)) {
            throw new IllegalArgumentException("Invalid metadata key: " + k);
          }
        });
  }

  /** Campo com chave normalizada e precedência padrão. */
  public static MetadataEntry normalized(
      String key, MetadataValue value, String namespace, String rawName) {
    return new MetadataEntry(Optional.of(key), value, rawName, namespace, DEFAULT_PRECEDENCE);
  }

  /** Campo sem chave normalizada. */
  public static MetadataEntry raw(String namespace, String rawName, MetadataValue value) {
    return new MetadataEntry(Optional.empty(), value, rawName, namespace, DEFAULT_PRECEDENCE);
  }

  /** Cópia com outra precedência. */
  public MetadataEntry withPrecedence(int newPrecedence) {
    return new MetadataEntry(key, value, rawName, namespace, newPrecedence);
  }
}
