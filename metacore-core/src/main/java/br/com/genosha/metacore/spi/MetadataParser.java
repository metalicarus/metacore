package br.com.genosha.metacore.spi;

import br.com.genosha.metacore.io.InputSource;
import java.io.IOException;
import java.util.Set;

/**
 * Extrai metadados de formatos conhecidos. Implementações devem ser thread-safe, não podem alterar
 * a fonte e devem chamar {@link ParseContext#checkCancelled()} entre etapas demoradas.
 *
 * <p>Problemas parciais são relatados com {@link ParseContext#warn}. Uma falha que impede a
 * extração pode ser sinalizada com {@code MetadataExtractionException} ou com a própria exceção de
 * I/O; o core converte ambas em resultado {@code FAILED}, mantendo o que já foi emitido.
 */
public interface MetadataParser {

  /** Identificador estável do parser, publicado como {@code source} de cada metadado. */
  String id();

  /**
   * Formatos que o parser entende. Um parser registrado para um supertipo também atende os
   * descendentes que não têm parser próprio.
   */
  Set<String> supportedFormats();

  /** Prioridade quando mais de um parser atende o mesmo formato; maior vence. */
  default int priority() {
    return 0;
  }

  /** Extrai os metadados da fonte, emitindo-os no contexto. */
  void parse(InputSource source, ParseContext context) throws IOException;
}
