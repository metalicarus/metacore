package br.com.genosha.metacore.internal;

import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.format.FormatRegistry;
import br.com.genosha.metacore.spi.MetadataParser;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Escolhe o parser de um formato: primeiro os que atendem o próprio formato, depois os dos
 * supertipos, do mais específico ao mais genérico. No mesmo nível, maior prioridade vence e o id
 * desempata.
 */
public final class ParserRegistry {

  private static final Comparator<MetadataParser> ORDER =
      Comparator.comparingInt(MetadataParser::priority)
          .reversed()
          .thenComparing(MetadataParser::id);

  private final List<MetadataParser> parsers;
  private final FormatRegistry formats;

  /**
   * @throws IllegalArgumentException se um parser declarar formato inexistente
   */
  public ParserRegistry(List<MetadataParser> parsers, FormatRegistry formats) {
    for (MetadataParser parser : parsers) {
      for (String formatId : parser.supportedFormats()) {
        if (formats.find(formatId).isEmpty()) {
          throw new IllegalArgumentException(
              "Parser '" + parser.id() + "' declares unknown format '" + formatId + "'");
        }
      }
    }
    this.parsers = parsers.stream().sorted(ORDER).toList();
    this.formats = formats;
  }

  public Optional<MetadataParser> find(String formatId) {
    for (FileFormat format : formats.lineage(formatId)) {
      Optional<MetadataParser> parser =
          parsers.stream().filter(p -> p.supportedFormats().contains(format.id())).findFirst();
      if (parser.isPresent()) {
        return parser;
      }
    }
    return Optional.empty();
  }
}
