package br.com.genosha.metacore.internal;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.exception.AnalysisCancelledException;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.TextValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.ParseContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Coletor de uma análise: aplica os limites, guarda campos e avisos e controla o cancelamento. */
public final class DefaultParseContext implements ParseContext {

  private final DetectionResult detection;
  private final AnalysisLimits limits;
  private final Optional<Long> deadlineNanos;
  private final List<MetadataEntry> entries = new ArrayList<>();
  private final List<Warning> warnings = new ArrayList<>();
  private boolean entryLimitReported;

  public DefaultParseContext(DetectionResult detection, AnalysisLimits limits, long startNanos) {
    this.detection = detection;
    this.limits = limits;
    this.deadlineNanos = limits.maxAnalysisTime().map(d -> startNanos + d.toNanos());
  }

  @Override
  public DetectionResult detection() {
    return detection;
  }

  @Override
  public AnalysisLimits limits() {
    return limits;
  }

  @Override
  public void emit(MetadataEntry entry) {
    if (entries.size() >= limits.maxMetadataEntries()) {
      if (!entryLimitReported) {
        entryLimitReported = true;
        warn(
            WarningCode.LIMIT_REACHED,
            "Reached maxMetadataEntries of "
                + limits.maxMetadataEntries()
                + "; further fields were discarded");
      }
      return;
    }
    MetadataValue limited = applyLimits(entry.value(), entry.rawName());
    entries.add(
        limited == entry.value()
            ? entry
            : new MetadataEntry(
                entry.key(), limited, entry.rawName(), entry.namespace(), entry.precedence()));
  }

  private MetadataValue applyLimits(MetadataValue value, String rawName) {
    return switch (value) {
      case TextValue text when text.value().length() > limits.maxValueLength() -> {
        warn(
            WarningCode.LIMIT_REACHED,
            "Text value truncated to maxValueLength of " + limits.maxValueLength() + " characters",
            rawName);
        yield new TextValue(text.value().substring(0, limits.maxValueLength()));
      }
      case BinaryValue binary
          when binary.hasContent() && binary.size() > limits.maxBinaryValueBytes() ->
          BinaryValue.sizeOnly(binary.size());
      case ListValue list -> {
        List<MetadataValue> limitedValues =
            list.values().stream().map(v -> applyLimits(v, rawName)).toList();
        yield limitedValues.equals(list.values()) ? list : new ListValue(limitedValues);
      }
      default -> value;
    };
  }

  @Override
  public void warn(WarningCode code, String message) {
    warnings.add(Warning.of(code, message));
  }

  @Override
  public void warn(WarningCode code, String message, String location) {
    warnings.add(Warning.of(code, message, location));
  }

  @Override
  public void checkCancelled() {
    if (Thread.currentThread().isInterrupted()) {
      throw new AnalysisCancelledException("Analysis thread was interrupted");
    }
    if (deadlineNanos.isPresent() && System.nanoTime() - deadlineNanos.get() > 0) {
      throw new AnalysisCancelledException(
          "Analysis exceeded maxAnalysisTime of " + limits.maxAnalysisTime().orElseThrow());
    }
  }

  /** Campos emitidos, na ordem de emissão. */
  public List<MetadataEntry> entries() {
    return List.copyOf(entries);
  }

  /** Avisos registrados, na ordem de registro. */
  public List<Warning> warnings() {
    return List.copyOf(warnings);
  }
}
