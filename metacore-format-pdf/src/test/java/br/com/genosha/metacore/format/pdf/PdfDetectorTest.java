package br.com.genosha.metacore.format.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PdfDetectorTest {

  private final PdfDetector detector = new PdfDetector();

  @Test
  void recognizesHeaderWithVersion() {
    DetectionCandidate candidate = detect("%PDF-1.7\n%âãÏÓ").orElseThrow();

    assertThat(candidate.formatId()).isEqualTo("pdf");
    assertThat(candidate.confidence()).isEqualTo(Confidence.EXACT);
    assertThat(candidate.evidence()).containsExactly(Evidence.MAGIC, Evidence.STRUCTURE);
  }

  @Test
  void recognizesHeaderWithoutVersionWithLowerConfidence() {
    assertThat(detect("%PDF-").orElseThrow().confidence()).isEqualTo(Confidence.HIGH);
  }

  @Test
  void findsHeaderAfterLeadingGarbage() {
    DetectionCandidate candidate = detect("garbage\r\n%PDF-2.0\n").orElseThrow();

    assertThat(candidate.confidence()).isEqualTo(Confidence.MEDIUM);
  }

  @Test
  void ignoresOtherContent() {
    assertThat(detect("%!PS-Adobe-3.0")).isEmpty();
    assertThat(detect(" ".repeat(1100) + "%PDF-1.7")).isEmpty();
  }

  private Optional<DetectionCandidate> detect(String header) {
    return detector.detect(
        DetectionRequest.of(header.getBytes(StandardCharsets.ISO_8859_1), Optional.empty()));
  }
}
