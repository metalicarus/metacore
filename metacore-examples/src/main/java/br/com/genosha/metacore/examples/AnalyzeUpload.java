package br.com.genosha.metacore.examples;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.metadata.MetadataKeys;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Validação de upload: aceita só imagens de até 20 MiB, com tempo de análise limitado, e devolve as
 * dimensões. Mostra como tratar cada desfecho: exceção (entrada recusada antes da análise) ou
 * estado no resultado.
 */
public final class AnalyzeUpload {

  private static final MetadataAnalyzer ANALYZER =
      MetadataAnalyzer.builder()
          .limits(
              AnalysisLimits.defaults()
                  .withMaxFileSize(20L * 1024 * 1024)
                  .withMaxAnalysisTime(Duration.ofSeconds(2)))
          .build();

  private AnalyzeUpload() {}

  /** Desfecho da validação. */
  public record Verdict(boolean accepted, String reason, Optional<String> dimensions) {}

  public static void main(String[] args) throws IOException {
    try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
      System.out.println(check(in, Path.of(args[0]).getFileName().toString()));
    }
  }

  /** O stream não é fechado pelo MetaCore; quem abriu fecha. */
  public static Verdict check(InputStream upload, String declaredName) {
    MetadataResult result;
    try {
      result = ANALYZER.analyze(upload, declaredName);
    } catch (InputLimitExceededException e) {
      return new Verdict(false, "too large", Optional.empty());
    } catch (InvalidInputException e) {
      return new Verdict(false, "unreadable: " + e.getMessage(), Optional.empty());
    }

    boolean isImage = result.format().map(f -> f.mimeType().startsWith("image/")).orElse(false);
    if (!isImage) {
      // A extensão não decide: um .jpg que não é JPEG cai aqui.
      return new Verdict(false, "not an image (" + result.status() + ")", Optional.empty());
    }
    if (result.status() == ExtractionStatus.FAILED) {
      return new Verdict(false, "corrupted image", Optional.empty());
    }
    Optional<String> dimensions =
        result
            .find(MetadataKeys.IMAGE_WIDTH)
            .flatMap(
                w -> result.find(MetadataKeys.IMAGE_HEIGHT).map(h -> w.value() + "x" + h.value()));
    String reason =
        result.status() == ExtractionStatus.PARTIAL ? "accepted with warnings" : "accepted";
    return new Verdict(true, reason, dimensions);
  }
}
