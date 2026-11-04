package br.com.genosha.metacore.comparison;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

/** Uma análise com cada biblioteca, do jeito que a documentação de cada uma recomenda. */
final class Runners {

  // Holders separados: a medição de primeira análise não pode carregar as duas bibliotecas.
  private static final class MetaCoreHolder {
    static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();
  }

  private static final class TikaHolder {
    static final Parser PARSER = new AutoDetectParser();
  }

  private Runners() {}

  /** Resultado do Tika: os metadados (sempre texto) e a exceção, se houve. */
  record TikaOutcome(Map<String, String[]> metadata, Throwable failure) {
    String contentType() {
      String[] values = metadata.get("Content-Type");
      return values == null ? "-" : values[0];
    }
  }

  static MetadataResult metacore(byte[] content, String fileName) {
    return MetaCoreHolder.ANALYZER.analyze(new ByteArrayInputStream(content), fileName);
  }

  static MetadataResult metacore(Path file) {
    return MetaCoreHolder.ANALYZER.analyze(file);
  }

  /** Arquivo aberto pelo próprio Tika, que assim tem acesso aleatório sem copiar o conteúdo. */
  static TikaOutcome tika(Path file) {
    Metadata metadata = new Metadata();
    Throwable failure = null;
    try (TikaInputStream in = TikaInputStream.get(file, metadata)) {
      TikaHolder.PARSER.parse(in, new DefaultHandler(), metadata, new ParseContext());
    } catch (Throwable t) {
      failure = t;
    }
    return new TikaOutcome(Map.of(), failure);
  }

  static TikaOutcome tika(byte[] content, String fileName) {
    Metadata metadata = new Metadata();
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
    Throwable failure = null;
    try (InputStream raw = new ByteArrayInputStream(content);
        TikaInputStream in = TikaInputStream.get(raw)) {
      // DefaultHandler descarta o texto: interessa só o que vai para os metadados.
      TikaHolder.PARSER.parse(in, new DefaultHandler(), metadata, new ParseContext());
    } catch (Throwable t) {
      failure = t;
    }
    Map<String, String[]> values = new TreeMap<>();
    for (String name : metadata.names()) {
      values.put(name, metadata.getValues(name));
    }
    return new TikaOutcome(values, failure);
  }

  static String describe(Throwable failure) {
    String message = String.valueOf(failure.getMessage());
    if (message.length() > 80) {
      message = message.substring(0, 80) + "...";
    }
    return failure.getClass().getSimpleName() + ": " + message.replace('|', '/').replace('\n', ' ');
  }

  static String join(String[] values) {
    return values.length == 1 ? values[0] : Arrays.toString(values);
  }
}
