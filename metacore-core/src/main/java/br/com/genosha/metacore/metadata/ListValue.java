package br.com.genosha.metacore.metadata;

import java.util.List;

/** Lista de valores, como as palavras-chave de um documento. */
public record ListValue(List<MetadataValue> values) implements MetadataValue {

  public ListValue {
    values = List.copyOf(values);
  }

  @Override
  public ValueType type() {
    return ValueType.LIST;
  }
}
