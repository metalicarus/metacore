package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.MetaCoreException;

/** Destino dos resultados num formato de saída. Recebe as entradas na ordem em que foram dadas. */
interface Report {

  void inspected(Input input, MetadataResult result);

  void identified(Input input, IdentificationResult result);

  void failed(Input input, MetaCoreException failure);

  /** Chamado uma vez, depois da última entrada. */
  default void finish(Tally tally) {}
}
