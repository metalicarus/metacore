package br.com.genosha.metacore.format.pdf;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Reconhece PDF pelo cabeçalho {@code %PDF-}. No início do arquivo e seguido de uma versão ({@code
 * 1.7}, {@code 2.0}), a confiança é {@code EXACT}. Leitores aceitam lixo antes do cabeçalho, então
 * ele também é procurado nos primeiros 1024 bytes, com confiança menor.
 */
public final class PdfDetector implements FormatDetector {

  private static final int[] HEADER = {'%', 'P', 'D', 'F', '-'};
  private static final int SEARCH_LIMIT = 1024;

  @Override
  public String id() {
    return "pdf-header";
  }

  @Override
  public Optional<DetectionCandidate> detect(DetectionRequest request) {
    String format = PdfFormats.PDF.id();
    if (request.startsWith(HEADER)) {
      if (hasVersion(request, HEADER.length)) {
        return Optional.of(
            new DetectionCandidate(
                format, Confidence.EXACT, EnumSet.of(Evidence.MAGIC, Evidence.STRUCTURE)));
      }
      return Optional.of(DetectionCandidate.of(format, Confidence.HIGH, Evidence.MAGIC));
    }
    int limit = Math.min(request.length(), SEARCH_LIMIT);
    for (int offset = 1; offset + HEADER.length <= limit; offset++) {
      if (request.matchesAt(offset, HEADER)) {
        return Optional.of(DetectionCandidate.of(format, Confidence.MEDIUM, Evidence.MAGIC));
      }
    }
    return Optional.empty();
  }

  private static boolean hasVersion(DetectionRequest request, int offset) {
    return request.length() >= offset + 3
        && isDigit(request.byteAt(offset))
        && request.byteAt(offset + 1) == '.'
        && isDigit(request.byteAt(offset + 2));
  }

  private static boolean isDigit(int value) {
    return value >= '0' && value <= '9';
  }
}
