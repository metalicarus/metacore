package br.com.genosha.metacore.examples;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataKeys;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Analisa um arquivo e mostra formato, estado, os valores principais das chaves normalizadas e os
 * avisos. Uso: {@code java ... InspectFile foto.jpg}.
 */
public final class InspectFile {

  /** Um analisador serve para a aplicação inteira: é imutável e thread-safe. */
  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  private InspectFile() {}

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("usage: InspectFile FILE");
      System.exit(2);
    }
    describe(Path.of(args[0])).forEach(System.out::println);
  }

  /** Linhas descrevendo o arquivo. */
  public static List<String> describe(Path file) {
    MetadataResult result = ANALYZER.analyze(file);
    List<String> lines = new ArrayList<>();
    lines.add(
        "format: "
            + result
                .format()
                .map(f -> f.displayName() + " (" + f.mimeType() + ")")
                .orElse("unknown"));
    lines.add("status: " + result.status());

    // Valores principais das chaves normalizadas, independentemente do formato.
    for (Metadata m : result.metadata()) {
      if (m.primary()) {
        lines.add(
            m.key().orElseThrow()
                + " = "
                + m.value()
                + "  ["
                + m.namespace()
                + " / "
                + m.rawName()
                + "]");
      }
    }

    // Consulta direta, com o tipo do valor: datas com fuso quando a fonte informa.
    result
        .find(MetadataKeys.IMAGE_DATETIME_ORIGINAL)
        .map(Metadata::value)
        .ifPresent(
            value -> {
              if (value instanceof OffsetDateTimeValue(var moment)) {
                lines.add("captured at " + moment.toInstant() + " (UTC)");
              } else {
                lines.add("captured at " + value + " (time zone unknown)");
              }
            });

    result.warnings().forEach(w -> lines.add("warning " + w.code() + ": " + w.message()));
    return lines;
  }
}
