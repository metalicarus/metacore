package br.com.genosha.metacore.examples;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.MetadataKeys;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.spi.ParseContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Um formato novo pela SPI: "notas", arquivos de texto que começam com {@code #notes} e têm linhas
 * {@code chave: valor}. Num módulo de verdade, o provedor seria registrado em {@code
 * META-INF/services/br.com.genosha.metacore.spi.MetadataProvider}; aqui ele é registrado pelo
 * builder.
 */
public final class CustomFormat {

  static final FileFormat NOTES = FileFormat.of("notes", "text/x-notes", "Notes", "notes");

  private CustomFormat() {}

  /** O provedor junta formato, detector e parser. */
  public static final class NotesProvider implements MetadataProvider {
    @Override
    public List<FileFormat> formats() {
      return List.of(NOTES);
    }

    @Override
    public List<FormatDetector> detectors() {
      return List.of(new NotesDetector());
    }

    @Override
    public List<MetadataParser> parsers() {
      return List.of(new NotesParser());
    }
  }

  /** Reconhece pela assinatura {@code #notes}, nunca só pela extensão. */
  static final class NotesDetector implements FormatDetector {
    @Override
    public String id() {
      return "notes-magic";
    }

    @Override
    public Optional<DetectionCandidate> detect(DetectionRequest request) {
      return request.startsWith('#', 'n', 'o', 't', 'e', 's')
          ? Optional.of(DetectionCandidate.of(NOTES.id(), Confidence.EXACT, Evidence.MAGIC))
          : Optional.empty();
    }
  }

  /** Cada linha vira um campo; {@code title} também vira a chave normalizada document.title. */
  static final class NotesParser implements MetadataParser {
    @Override
    public String id() {
      return "notes";
    }

    @Override
    public Set<String> supportedFormats() {
      return Set.of(NOTES.id());
    }

    @Override
    public void parse(InputSource source, ParseContext context) throws IOException {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(source.openStream(), StandardCharsets.UTF_8))) {
        reader.readLine(); // assinatura
        String line;
        int number = 1;
        while ((line = reader.readLine()) != null) {
          number++;
          context.checkCancelled();
          int colon = line.indexOf(':');
          if (colon <= 0) {
            context.warn(
                WarningCode.CORRUPTED_SEGMENT, "Line without 'key: value'", "line " + number);
            continue;
          }
          String key = line.substring(0, colon).trim();
          MetadataValue value = MetadataValue.of(line.substring(colon + 1).trim());
          context.emit(
              key.equals("title")
                  ? MetadataEntry.normalized(MetadataKeys.DOCUMENT_TITLE, value, "NOTES", key)
                  : MetadataEntry.raw("NOTES", key, value));
        }
      }
    }
  }

  /** Analisador com os formatos padrão mais o formato de notas. */
  public static MetadataAnalyzer analyzer() {
    return MetadataAnalyzer.builder().addProvider(new NotesProvider()).build();
  }

  public static void main(String[] args) {
    MetadataResult result = analyzer().analyze(java.nio.file.Path.of(args[0]));
    System.out.println(result.status() + " " + result.find(MetadataKeys.DOCUMENT_TITLE));
  }
}
