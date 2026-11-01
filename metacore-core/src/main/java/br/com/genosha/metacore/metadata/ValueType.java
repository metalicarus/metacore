package br.com.genosha.metacore.metadata;

/** Tipo lógico de um {@link MetadataValue}. É o valor de {@code valueType} no JSON. */
public enum ValueType {
  TEXT,
  INTEGER,
  DECIMAL,
  RATIONAL,
  BOOLEAN,
  LOCAL_DATE_TIME,
  OFFSET_DATE_TIME,
  BINARY,
  LIST
}
