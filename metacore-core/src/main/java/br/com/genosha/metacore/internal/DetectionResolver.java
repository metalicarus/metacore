package br.com.genosha.metacore.internal;

import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.format.FormatRegistry;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executa os detectores e escolhe o vencedor pelas regras do ADR 0004. */
public final class DetectionResolver {

  private static final Logger LOG = LoggerFactory.getLogger(DetectionResolver.class);

  private final List<FormatDetector> detectors;
  private final FormatRegistry formats;

  public DetectionResolver(List<FormatDetector> detectors, FormatRegistry formats) {
    this.detectors = List.copyOf(detectors);
    this.formats = formats;
  }

  /** Resultado da resolução e os avisos produzidos nela. */
  public record Outcome(Optional<DetectionResult> result, List<Warning> warnings) {}

  private record Claim(DetectionCandidate candidate, FormatDetector detector, FileFormat format) {}

  private static final Comparator<Claim> STRENGTH =
      Comparator.comparing((Claim c) -> c.candidate().confidence())
          .thenComparingInt(c -> c.detector().priority());

  public Outcome resolve(DetectionRequest request) {
    List<Warning> warnings = new ArrayList<>();
    List<Claim> claims = collectClaims(request, warnings);
    if (claims.isEmpty()) {
      return new Outcome(Optional.empty(), List.copyOf(warnings));
    }

    // Regra 1: evidência interna vence evidência só externa.
    if (claims.stream().anyMatch(DetectionResolver::hasInternalEvidence)) {
      claims = claims.stream().filter(DetectionResolver::hasInternalEvidence).toList();
    }

    // Regra 2: entre formatos da mesma linhagem, o mais específico vence.
    List<Claim> current = claims;
    claims =
        current.stream()
            .filter(
                c ->
                    current.stream()
                        .noneMatch(
                            other ->
                                !other.format().id().equals(c.format().id())
                                    && formats.isSameOrDescendant(
                                        other.format().id(), c.format().id())))
            .toList();

    // Regras 3 e 4: maior confiança, depois maior prioridade do detector.
    Claim strongest = claims.stream().max(STRENGTH).orElseThrow();
    List<Claim> tied =
        claims.stream()
            .filter(c -> STRENGTH.compare(c, strongest) == 0)
            .sorted(Comparator.comparing(c -> c.detector().id()))
            .toList();

    // Regra 5: desempate determinístico pelo id do detector.
    Claim winner = tied.get(0);
    Set<String> tiedFormats = new TreeSet<>();
    tied.forEach(c -> tiedFormats.add(c.format().id()));
    if (tiedFormats.size() > 1) {
      warnings.add(
          Warning.of(
              WarningCode.AMBIGUOUS_DETECTION,
              "Detectors disagree with equal strength between formats "
                  + tiedFormats
                  + "; chose '"
                  + winner.format().id()
                  + "' from detector '"
                  + winner.detector().id()
                  + "'"));
    }

    FileFormat format = winner.format();
    Set<Evidence> evidence = EnumSet.copyOf(winner.candidate().evidence());
    Optional<String> extension = request.extension();
    if (extension.isPresent()) {
      if (format.hasExtension(extension.get())) {
        evidence.add(Evidence.EXTENSION);
      } else {
        warnings.add(
            Warning.of(
                WarningCode.EXTENSION_MISMATCH,
                "File extension '"
                    + extension.get()
                    + "' does not match detected format "
                    + format.displayName()));
      }
    }
    DetectionResult result =
        new DetectionResult(
            format, winner.candidate().confidence(), evidence, winner.detector().id());
    return new Outcome(Optional.of(result), List.copyOf(warnings));
  }

  private List<Claim> collectClaims(DetectionRequest request, List<Warning> warnings) {
    List<Claim> claims = new ArrayList<>();
    for (FormatDetector detector : detectors) {
      Optional<DetectionCandidate> candidate;
      try {
        candidate = detector.detect(request);
      } catch (RuntimeException e) {
        LOG.debug("Detector '{}' failed", detector.id(), e);
        warnings.add(
            Warning.of(
                WarningCode.DETECTOR_ERROR,
                "Detector '" + detector.id() + "' failed: " + Failures.describe(e)));
        continue;
      }
      if (candidate == null || candidate.isEmpty()) {
        continue;
      }
      Optional<FileFormat> format = formats.find(candidate.get().formatId());
      if (format.isEmpty()) {
        warnings.add(
            Warning.of(
                WarningCode.DETECTOR_ERROR,
                "Detector '"
                    + detector.id()
                    + "' reported unknown format '"
                    + candidate.get().formatId()
                    + "'"));
        continue;
      }
      claims.add(new Claim(candidate.get(), detector, format.get()));
    }
    return claims;
  }

  private static boolean hasInternalEvidence(Claim claim) {
    return claim.candidate().evidence().stream().anyMatch(Evidence::isInternal);
  }
}
