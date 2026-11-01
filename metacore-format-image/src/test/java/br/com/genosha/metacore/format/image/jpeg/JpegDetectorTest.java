package br.com.genosha.metacore.format.image.jpeg;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JpegDetectorTest {

  private final JpegDetector detector = new JpegDetector();

  @Test
  void recognizesSoiFollowedByAMarkerWithFullConfidence() {
    DetectionCandidate candidate = detect(0xFF, 0xD8, 0xFF, 0xE0).orElseThrow();

    assertThat(candidate.formatId()).isEqualTo("jpeg");
    assertThat(candidate.confidence()).isEqualTo(Confidence.EXACT);
    assertThat(candidate.evidence()).containsExactly(Evidence.MAGIC, Evidence.STRUCTURE);
  }

  @Test
  void recognizesABareSignatureWithLowerConfidence() {
    assertThat(detect(0xFF, 0xD8, 0xFF).orElseThrow().confidence()).isEqualTo(Confidence.HIGH);
    assertThat(detect(0xFF, 0xD8, 0xFF, 0x00).orElseThrow().confidence())
        .isEqualTo(Confidence.HIGH);
  }

  @Test
  void ignoresOtherContent() {
    assertThat(detect(0x89, 'P', 'N', 'G')).isEmpty();
    assertThat(detect(0xFF, 0xD8)).isEmpty();
    assertThat(detect()).isEmpty();
  }

  private Optional<DetectionCandidate> detect(int... bytes) {
    byte[] header = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      header[i] = (byte) bytes[i];
    }
    return detector.detect(DetectionRequest.of(header, Optional.empty()));
  }
}
