package br.com.genosha.metacore.format;

/**
 * Confiança de uma detecção. A ordem de declaração é a ordem crescente de confiança, então {@link
 * #compareTo} pode ser usado para comparar valores.
 */
public enum Confidence {
  LOW,
  MEDIUM,
  HIGH,
  EXACT
}
