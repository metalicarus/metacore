package br.com.genosha.metacore.internal;

import br.com.genosha.metacore.metadata.LocalDateTimeValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marca o valor principal de cada chave (menor precedência; empate fica com o primeiro emitido) e
 * aponta divergências entre valores da mesma chave (ADR 0005).
 */
public final class PrimarySelector {

  private PrimarySelector() {}

  /** Campos finais e avisos de conflito. */
  public record Selection(List<Metadata> metadata, List<Warning> warnings) {}

  public static Selection select(List<MetadataEntry> entries, String source) {
    Map<String, Integer> primaryIndex = new HashMap<>();
    Map<String, List<MetadataValue>> valuesByKey = new LinkedHashMap<>();
    for (int i = 0; i < entries.size(); i++) {
      MetadataEntry entry = entries.get(i);
      if (entry.key().isEmpty()) {
        continue;
      }
      String key = entry.key().get();
      valuesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.value());
      Integer best = primaryIndex.get(key);
      if (best == null || entry.precedence() < entries.get(best).precedence()) {
        primaryIndex.put(key, i);
      }
    }

    List<Metadata> metadata = new ArrayList<>(entries.size());
    for (int i = 0; i < entries.size(); i++) {
      MetadataEntry entry = entries.get(i);
      boolean primary = entry.key().map(k -> primaryIndex.get(k)).orElse(-1) == i;
      metadata.add(
          new Metadata(
              entry.key(), entry.value(), entry.rawName(), entry.namespace(), source, primary));
    }

    List<Warning> warnings = new ArrayList<>();
    valuesByKey.forEach(
        (key, values) -> {
          MetadataValue primary = entries.get(primaryIndex.get(key)).value();
          if (values.stream().anyMatch(v -> !equivalent(primary, v))) {
            warnings.add(
                Warning.of(
                    WarningCode.METADATA_CONFLICT,
                    "Sources disagree on the value of '" + key + "'",
                    key));
          }
        });
    return new Selection(List.copyOf(metadata), List.copyOf(warnings));
  }

  /** Valores iguais, ou a mesma data e hora local com e sem fuso. */
  static boolean equivalent(MetadataValue a, MetadataValue b) {
    if (a.equals(b)) {
      return true;
    }
    if (a instanceof LocalDateTimeValue local && b instanceof OffsetDateTimeValue offset) {
      return local.value().equals(offset.value().toLocalDateTime());
    }
    if (a instanceof OffsetDateTimeValue offset && b instanceof LocalDateTimeValue local) {
      return local.value().equals(offset.value().toLocalDateTime());
    }
    return false;
  }
}
