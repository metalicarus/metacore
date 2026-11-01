package br.com.genosha.metacore.format.image.jpeg;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.image.ImageFormats;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Reconhece JPEG pelo marcador SOI ({@code FF D8}) seguido do início de outro marcador ({@code
 * FF}). Quando o quarto byte também é um código de marcador válido, a confiança é {@code EXACT}.
 */
public final class JpegDetector implements FormatDetector {

  @Override
  public String id() {
    return "jpeg-magic";
  }

  @Override
  public Optional<DetectionCandidate> detect(DetectionRequest request) {
    if (!request.startsWith(0xFF, 0xD8, 0xFF)) {
      return Optional.empty();
    }
    String format = ImageFormats.JPEG.id();
    if (request.length() > 3 && request.byteAt(3) >= 0xC0 && request.byteAt(3) <= 0xFE) {
      return Optional.of(
          new DetectionCandidate(
              format, Confidence.EXACT, EnumSet.of(Evidence.MAGIC, Evidence.STRUCTURE)));
    }
    return Optional.of(DetectionCandidate.of(format, Confidence.HIGH, Evidence.MAGIC));
  }
}
