package br.com.genosha.metacore.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Limites de recursos de uma análise. Os valores padrão e as medições que os embasam estão em
 * {@code docs/performance.md}.
 *
 * @param maxFileSize tamanho máximo aceito, em bytes
 * @param maxDetectionBytes bytes lidos do início para detecção
 * @param spoolMemoryThreshold até quantos bytes um {@code InputStream} fica em memória
 * @param maxMetadataEntries número máximo de campos por resultado
 * @param maxValueLength tamanho máximo de um valor de texto, em caracteres
 * @param maxBinaryValueBytes tamanho máximo do conteúdo guardado de um valor binário
 * @param maxDecompressedTextBytes limite para texto compactado, como PNG {@code zTXt}
 * @param maxAnalysisTime tempo máximo da análise, verificado de forma cooperativa
 */
public record AnalysisLimits(
    long maxFileSize,
    int maxDetectionBytes,
    int spoolMemoryThreshold,
    int maxMetadataEntries,
    int maxValueLength,
    int maxBinaryValueBytes,
    long maxDecompressedTextBytes,
    Optional<Duration> maxAnalysisTime) {

  private static final int MAX_ARRAY = Integer.MAX_VALUE - 8;

  public AnalysisLimits {
    requirePositive(maxFileSize, "maxFileSize");
    requirePositive(maxDetectionBytes, "maxDetectionBytes");
    requirePositive(maxMetadataEntries, "maxMetadataEntries");
    requirePositive(maxValueLength, "maxValueLength");
    requirePositive(maxDecompressedTextBytes, "maxDecompressedTextBytes");
    if (spoolMemoryThreshold < 0 || spoolMemoryThreshold >= MAX_ARRAY) {
      throw new IllegalArgumentException("Invalid spoolMemoryThreshold: " + spoolMemoryThreshold);
    }
    if (maxBinaryValueBytes < 0) {
      throw new IllegalArgumentException("maxBinaryValueBytes must not be negative");
    }
    Objects.requireNonNull(maxAnalysisTime, "maxAnalysisTime");
    maxAnalysisTime.ifPresent(
        d -> {
          if (d.isNegative() || d.isZero()) {
            throw new IllegalArgumentException("maxAnalysisTime must be positive");
          }
        });
  }

  /** Limites padrão. */
  public static AnalysisLimits defaults() {
    return new AnalysisLimits(
        512L * 1024 * 1024,
        8 * 1024,
        8 * 1024 * 1024,
        10_000,
        64 * 1024,
        64 * 1024,
        1024 * 1024,
        Optional.empty());
  }

  public AnalysisLimits withMaxFileSize(long bytes) {
    return new AnalysisLimits(
        bytes,
        maxDetectionBytes,
        spoolMemoryThreshold,
        maxMetadataEntries,
        maxValueLength,
        maxBinaryValueBytes,
        maxDecompressedTextBytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withMaxDetectionBytes(int bytes) {
    return new AnalysisLimits(
        maxFileSize,
        bytes,
        spoolMemoryThreshold,
        maxMetadataEntries,
        maxValueLength,
        maxBinaryValueBytes,
        maxDecompressedTextBytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withSpoolMemoryThreshold(int bytes) {
    return new AnalysisLimits(
        maxFileSize,
        maxDetectionBytes,
        bytes,
        maxMetadataEntries,
        maxValueLength,
        maxBinaryValueBytes,
        maxDecompressedTextBytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withMaxMetadataEntries(int entries) {
    return new AnalysisLimits(
        maxFileSize,
        maxDetectionBytes,
        spoolMemoryThreshold,
        entries,
        maxValueLength,
        maxBinaryValueBytes,
        maxDecompressedTextBytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withMaxValueLength(int characters) {
    return new AnalysisLimits(
        maxFileSize,
        maxDetectionBytes,
        spoolMemoryThreshold,
        maxMetadataEntries,
        characters,
        maxBinaryValueBytes,
        maxDecompressedTextBytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withMaxBinaryValueBytes(int bytes) {
    return new AnalysisLimits(
        maxFileSize,
        maxDetectionBytes,
        spoolMemoryThreshold,
        maxMetadataEntries,
        maxValueLength,
        bytes,
        maxDecompressedTextBytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withMaxDecompressedTextBytes(long bytes) {
    return new AnalysisLimits(
        maxFileSize,
        maxDetectionBytes,
        spoolMemoryThreshold,
        maxMetadataEntries,
        maxValueLength,
        maxBinaryValueBytes,
        bytes,
        maxAnalysisTime);
  }

  public AnalysisLimits withMaxAnalysisTime(Duration duration) {
    return new AnalysisLimits(
        maxFileSize,
        maxDetectionBytes,
        spoolMemoryThreshold,
        maxMetadataEntries,
        maxValueLength,
        maxBinaryValueBytes,
        maxDecompressedTextBytes,
        Optional.of(duration));
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
