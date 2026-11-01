package br.com.genosha.metacore.spi;

import br.com.genosha.metacore.format.FileFormat;
import java.util.List;

/**
 * Ponto de entrada de um módulo de formato. Implementações são descobertas por {@link
 * java.util.ServiceLoader} a partir de {@code
 * META-INF/services/br.com.genosha.metacore.spi.MetadataProvider} e precisam de um construtor
 * público sem argumentos.
 */
public interface MetadataProvider {

  /** Formatos que este módulo declara. */
  default List<FileFormat> formats() {
    return List.of();
  }

  /** Detectores fornecidos pelo módulo. */
  default List<FormatDetector> detectors() {
    return List.of();
  }

  /** Parsers fornecidos pelo módulo. */
  default List<MetadataParser> parsers() {
    return List.of();
  }
}
