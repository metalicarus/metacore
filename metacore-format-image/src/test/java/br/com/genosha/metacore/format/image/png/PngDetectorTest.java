package br.com.genosha.metacore.format.image.png;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.image.fixtures.PngFixtures;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PngDetectorTest {

  private final PngDetector detector = new PngDetector();

  @Test
  void recognizesSignatureAndIhdr() {
    DetectionCandidate candidate = detect(PngFixtures.plain()).orElseThrow();

    assertThat(candidate.formatId()).isEqualTo("png");
    assertThat(candidate.confidence()).isEqualTo(Confidence.EXACT);
    assertThat(candidate.evidence()).containsExactly(Evidence.MAGIC, Evidence.STRUCTURE);
  }

  @Test
  void recognizesABareSignature() {
    byte[] signatureOnly = Arrays.copyOf(PngFixtures.plain(), 8);

    assertThat(detect(signatureOnly).orElseThrow().evidence()).containsExactly(Evidence.MAGIC);
  }

  @Test
  void ignoresOtherContent() {
    assertThat(detect(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
    assertThat(detect("PNG image".getBytes())).isEmpty();
  }

  private Optional<DetectionCandidate> detect(byte[] header) {
    return detector.detect(DetectionRequest.of(header, Optional.empty()));
  }
}
