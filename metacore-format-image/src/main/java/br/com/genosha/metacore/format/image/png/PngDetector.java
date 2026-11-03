package br.com.genosha.metacore.format.image.png;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.image.ImageFormats;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Reconhece PNG pela assinatura de 8 bytes. Quando o primeiro chunk é o IHDR, como exige a
 * especificação, a evidência inclui a estrutura.
 */
public final class PngDetector implements FormatDetector {

  @Override
  public String id() {
    return "png-magic";
  }

  @Override
  public Optional<DetectionCandidate> detect(DetectionRequest request) {
    if (!request.startsWith(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return Optional.empty();
    }
    EnumSet<Evidence> evidence = EnumSet.of(Evidence.MAGIC);
    if (request.matchesAt(12, 'I', 'H', 'D', 'R')) {
      evidence.add(Evidence.STRUCTURE);
    }
    return Optional.of(new DetectionCandidate(ImageFormats.PNG.id(), Confidence.EXACT, evidence));
  }
}
