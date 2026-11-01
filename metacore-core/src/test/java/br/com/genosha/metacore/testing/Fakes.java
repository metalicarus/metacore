package br.com.genosha.metacore.testing;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.spi.ParseContext;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Formatos, detectores, parsers e provedores falsos para testar o core sem formatos reais. */
public final class Fakes {

  public static final FileFormat ALPHA =
      FileFormat.of("alpha", "application/x-alpha", "Alpha", "alp");
  public static final FileFormat BETA = FileFormat.of("beta", "application/x-beta", "Beta", "bet");
  public static final FileFormat ZIP = FileFormat.of("zip", "application/zip", "ZIP", "zip");
  public static final FileFormat DOCX =
      FileFormat.of(
              "docx",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "DOCX",
              "docx")
          .withParent("zip");

  /** Conteúdo reconhecido como {@link #ALPHA} pelo detector de {@link #alphaDetector()}. */
  public static final byte[] ALPHA_CONTENT = {'A', 'L', 'P', 'H', 1, 2, 3};

  /** Conteúdo que nenhum detector falso reconhece. */
  public static final byte[] UNKNOWN_CONTENT = {0, 0, 0, 0, 0};

  /** Conteúdo com assinatura ZIP. */
  public static final byte[] ZIP_CONTENT = {'P', 'K', 3, 4, 0, 0};

  private Fakes() {}

  /** Corpo de um parser falso. */
  @FunctionalInterface
  public interface ParseBody {
    void parse(InputSource source, ParseContext context) throws IOException;
  }

  public static FormatDetector detector(
      String id, int priority, Function<DetectionRequest, Optional<DetectionCandidate>> logic) {
    return new FormatDetector() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public int priority() {
        return priority;
      }

      @Override
      public Optional<DetectionCandidate> detect(DetectionRequest request) {
        return logic.apply(request);
      }
    };
  }

  /** Detector que reconhece o formato pela assinatura, com a confiança informada. */
  public static FormatDetector magic(
      String id, String formatId, Confidence confidence, int... signature) {
    return detector(
        id,
        0,
        request ->
            request.startsWith(signature)
                ? Optional.of(DetectionCandidate.of(formatId, confidence, Evidence.MAGIC))
                : Optional.empty());
  }

  /** Detector que reconhece o formato só pela extensão. */
  public static FormatDetector byExtension(String id, String formatId, String extension) {
    return detector(
        id,
        0,
        request ->
            request.extension().filter(extension::equals).isPresent()
                ? Optional.of(DetectionCandidate.of(formatId, Confidence.LOW, Evidence.EXTENSION))
                : Optional.empty());
  }

  /** Detector de {@link #ALPHA} pela assinatura {@code ALPH}. */
  public static FormatDetector alphaDetector() {
    return magic("alpha-magic", "alpha", Confidence.EXACT, 'A', 'L', 'P', 'H');
  }

  /** Detector de {@link #ZIP} pela assinatura {@code PK\3\4}. */
  public static FormatDetector zipDetector() {
    return magic("zip-magic", "zip", Confidence.EXACT, 'P', 'K', 3, 4);
  }

  public static MetadataParser parser(String id, Set<String> formats, ParseBody body) {
    return parser(id, 0, formats, body);
  }

  public static MetadataParser parser(
      String id, int priority, Set<String> formats, ParseBody body) {
    return new MetadataParser() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public Set<String> supportedFormats() {
        return formats;
      }

      @Override
      public int priority() {
        return priority;
      }

      @Override
      public void parse(InputSource source, ParseContext context) throws IOException {
        body.parse(source, context);
      }
    };
  }

  public static MetadataProvider provider(
      List<FileFormat> formats, List<FormatDetector> detectors, List<MetadataParser> parsers) {
    return new MetadataProvider() {
      @Override
      public List<FileFormat> formats() {
        return formats;
      }

      @Override
      public List<FormatDetector> detectors() {
        return detectors;
      }

      @Override
      public List<MetadataParser> parsers() {
        return parsers;
      }
    };
  }
}
