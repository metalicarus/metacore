package br.com.genosha.metacore.internal;

import static br.com.genosha.metacore.testing.Fakes.ALPHA;
import static br.com.genosha.metacore.testing.Fakes.BETA;
import static br.com.genosha.metacore.testing.Fakes.DOCX;
import static br.com.genosha.metacore.testing.Fakes.ZIP;
import static br.com.genosha.metacore.testing.Fakes.detector;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FormatRegistry;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DetectionResolverTest {

  private static final FormatRegistry FORMATS = FormatRegistry.of(List.of(ALPHA, BETA, ZIP, DOCX));
  private static final DetectionRequest ANY = DetectionRequest.of(new byte[] {1}, Optional.empty());

  @Test
  void returnsEmptyWhenNoDetectorRecognizesTheContent() {
    DetectionResolver.Outcome outcome =
        resolve(ANY, claiming("d", 0, "alpha", Confidence.EXACT, null));

    assertThat(outcome.result()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  void internalEvidenceBeatsExtensionOnly() {
    DetectionResolver.Outcome outcome =
        resolve(
            ANY,
            claiming("by-extension", 0, "alpha", Confidence.EXACT, Evidence.EXTENSION),
            claiming("by-magic", 0, "beta", Confidence.LOW, Evidence.MAGIC));

    assertThat(winner(outcome).format()).isEqualTo(BETA);
  }

  @Test
  void mostSpecificFormatWinsWithinTheSameLineage() {
    DetectionResolver.Outcome outcome =
        resolve(
            ANY,
            claiming("zip", 0, "zip", Confidence.EXACT, Evidence.MAGIC),
            claiming("docx", 0, "docx", Confidence.LOW, Evidence.STRUCTURE));

    assertThat(winner(outcome).format()).isEqualTo(DOCX);
    assertThat(winner(outcome).detectorId()).isEqualTo("docx");
  }

  @Test
  void higherConfidenceWins() {
    DetectionResolver.Outcome outcome =
        resolve(
            ANY,
            claiming("a", 9, "alpha", Confidence.MEDIUM, Evidence.MAGIC),
            claiming("b", 0, "beta", Confidence.HIGH, Evidence.MAGIC));

    assertThat(winner(outcome).format()).isEqualTo(BETA);
    assertThat(winner(outcome).confidence()).isEqualTo(Confidence.HIGH);
  }

  @Test
  void higherPriorityBreaksConfidenceTies() {
    DetectionResolver.Outcome outcome =
        resolve(
            ANY,
            claiming("a", 0, "alpha", Confidence.HIGH, Evidence.MAGIC),
            claiming("b", 5, "beta", Confidence.HIGH, Evidence.MAGIC));

    assertThat(winner(outcome).format()).isEqualTo(BETA);
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  void persistentTieIsResolvedByDetectorIdAndReported() {
    DetectionResolver.Outcome outcome =
        resolve(
            ANY,
            claiming("zeta", 0, "alpha", Confidence.HIGH, Evidence.MAGIC),
            claiming("eta", 0, "beta", Confidence.HIGH, Evidence.MAGIC));

    assertThat(winner(outcome).detectorId()).isEqualTo("eta");
    assertThat(outcome.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.AMBIGUOUS_DETECTION);
  }

  @Test
  void agreementOnTheSameFormatIsNotAmbiguous() {
    DetectionResolver.Outcome outcome =
        resolve(
            ANY,
            claiming("one", 0, "alpha", Confidence.HIGH, Evidence.MAGIC),
            claiming("two", 0, "alpha", Confidence.HIGH, Evidence.MAGIC));

    assertThat(winner(outcome).detectorId()).isEqualTo("one");
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  void matchingExtensionIsAddedAsEvidence() {
    DetectionRequest request = DetectionRequest.of(new byte[] {1}, Optional.of("file.ALP"));

    DetectionResolver.Outcome outcome =
        resolve(request, claiming("a", 0, "alpha", Confidence.EXACT, Evidence.MAGIC));

    assertThat(winner(outcome).evidence()).containsExactly(Evidence.MAGIC, Evidence.EXTENSION);
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  void mismatchingExtensionIsReported() {
    DetectionRequest request = DetectionRequest.of(new byte[] {1}, Optional.of("file.bet"));

    DetectionResolver.Outcome outcome =
        resolve(request, claiming("a", 0, "alpha", Confidence.EXACT, Evidence.MAGIC));

    assertThat(winner(outcome).evidence()).containsExactly(Evidence.MAGIC);
    assertThat(outcome.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.EXTENSION_MISMATCH);
  }

  @Test
  void failingDetectorIsReportedAndSkipped() {
    FormatDetector broken =
        detector(
            "broken",
            0,
            request -> {
              throw new IllegalStateException("boom");
            });

    DetectionResolver.Outcome outcome =
        resolve(ANY, broken, claiming("a", 0, "alpha", Confidence.EXACT, Evidence.MAGIC));

    assertThat(winner(outcome).format()).isEqualTo(ALPHA);
    assertThat(outcome.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.DETECTOR_ERROR);
              assertThat(w.message()).contains("broken", "IllegalStateException: boom");
            });
  }

  @Test
  void unknownFormatFromDetectorIsReportedAndSkipped() {
    DetectionResolver.Outcome outcome =
        resolve(ANY, claiming("a", 0, "missing", Confidence.EXACT, Evidence.MAGIC));

    assertThat(outcome.result()).isEmpty();
    assertThat(outcome.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.DETECTOR_ERROR);
  }

  /** Detector que sempre reivindica o formato; com {@code evidence} nulo, nunca reivindica. */
  private static FormatDetector claiming(
      String id, int priority, String formatId, Confidence confidence, Evidence evidence) {
    return detector(
        id,
        priority,
        request ->
            evidence == null
                ? Optional.empty()
                : Optional.of(DetectionCandidate.of(formatId, confidence, evidence)));
  }

  private static DetectionResolver.Outcome resolve(
      DetectionRequest request, FormatDetector... detectors) {
    return new DetectionResolver(List.of(detectors), FORMATS).resolve(request);
  }

  private static DetectionResult winner(DetectionResolver.Outcome outcome) {
    return outcome.result().orElseThrow();
  }
}
