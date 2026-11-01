package br.com.genosha.metacore.spi;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.metadata.WarningCode;

/**
 * Canal entre o parser e o core durante uma análise. Uma instância serve a uma única análise e não
 * deve ser compartilhada entre threads.
 */
public interface ParseContext {

  /** Detecção que levou à escolha do parser. */
  DetectionResult detection();

  /**
   * Limites da análise. Os limites de quantidade e tamanho de metadados já são aplicados em {@link
   * #emit}.
   */
  AnalysisLimits limits();

  /**
   * Emite um campo. Acima de {@code maxMetadataEntries} o campo é descartado; texto acima de {@code
   * maxValueLength} é truncado; conteúdo binário acima de {@code maxBinaryValueBytes} é descartado,
   * mantendo o tamanho. Os dois primeiros casos geram aviso {@code LIMIT_REACHED}.
   */
  void emit(MetadataEntry entry);

  /** Registra um aviso. */
  void warn(WarningCode code, String message);

  /** Registra um aviso com a localização do problema. */
  void warn(WarningCode code, String message, String location);

  /**
   * Lança {@link br.com.genosha.metacore.exception.AnalysisCancelledException} se a análise passou
   * de {@code maxAnalysisTime} ou se a thread foi interrompida.
   */
  void checkCancelled();
}
