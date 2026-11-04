package br.com.genosha.metacore.isolation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.FileInfo;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ProtocolTest {

  private static final AnalysisLimits LIMITS = AnalysisLimits.defaults();

  @Test
  void roundTripsEveryKindOfValue() throws IOException {
    FileFormat format =
        new FileFormat("x-test", "application/x-test", "Test", List.of("xt"), Optional.of("zip"));
    DetectionResult detection =
        new DetectionResult(
            format, Confidence.HIGH, EnumSet.of(Evidence.MAGIC, Evidence.EXTENSION), "det");
    List<MetadataValue> values =
        List.of(
            MetadataValue.of("ção 🎉"),
            MetadataValue.of(Long.MIN_VALUE),
            MetadataValue.of(new BigDecimal("-23.5500000")),
            MetadataValue.of(new BigDecimal("1E+400")),
            MetadataValue.rational(2, 250),
            MetadataValue.of(true),
            MetadataValue.of(LocalDateTime.of(1601, 1, 1, 0, 0, 0, 123_456_789)),
            MetadataValue.of(OffsetDateTime.of(2026, 7, 14, 16, 32, 5, 0, ZoneOffset.ofHours(-3))),
            BinaryValue.of(new byte[] {0, (byte) 0xFF, 7}),
            BinaryValue.sizeOnly(1L << 40),
            new ListValue(List.of(MetadataValue.of("a"), new ListValue(List.of()))));
    List<Metadata> metadata =
        values.stream()
            .map(v -> new Metadata(Optional.empty(), v, "raw", "NS:Group", "parser", false))
            .toList();
    MetadataResult result =
        new MetadataResult(
            FileInfo.of(Optional.of("a.xt"), OptionalLong.of(42)),
            Optional.of(detection),
            ExtractionStatus.PARTIAL,
            List.of(
                new Metadata(
                    Optional.of("document.title"),
                    MetadataValue.of("T"),
                    "Title",
                    "PDF:Info",
                    "pdf",
                    true)),
            List.of(
                Warning.of(WarningCode.TRUNCATED, "cut", "offset 9"),
                Warning.of(new WarningCode("CUSTOM_CODE", false), "x")),
            Duration.ofNanos(123_456));
    MetadataResult withValues =
        new MetadataResult(
            result.file(),
            result.detection(),
            result.status(),
            concat(result.metadata(), metadata),
            result.warnings(),
            result.duration());

    assertThat(roundTrip(withValues)).isEqualTo(withValues);
    MetadataResult empty =
        new MetadataResult(
            FileInfo.of(Optional.empty(), OptionalLong.empty()),
            Optional.empty(),
            ExtractionStatus.UNKNOWN_FORMAT,
            List.of(),
            List.of(),
            Duration.ZERO);
    assertThat(roundTrip(empty)).isEqualTo(empty);
  }

  @Test
  void roundTripsLimits() throws IOException {
    AnalysisLimits limits =
        AnalysisLimits.defaults().withMaxFileSize(10).withMaxAnalysisTime(Duration.ofMillis(5));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Protocol.writeLimits(new DataOutputStream(bytes), limits);

    assertThat(Protocol.readLimits(input(bytes.toByteArray()))).isEqualTo(limits);
  }

  @Test
  void refusesMoreEntriesThanTheLimitsAllow() throws IOException {
    MetadataResult result =
        new MetadataResult(
            FileInfo.of(Optional.empty(), OptionalLong.empty()),
            Optional.empty(),
            ExtractionStatus.SUCCESS,
            List.of(
                new Metadata(Optional.empty(), MetadataValue.of(1), "a", "N", "p", false),
                new Metadata(Optional.empty(), MetadataValue.of(2), "b", "N", "p", false)),
            List.of(),
            Duration.ZERO);
    byte[] encoded = encode(result);

    assertThatThrownBy(
            () ->
                Protocol.readResult(
                    input(encoded), AnalysisLimits.defaults().withMaxMetadataEntries(1)))
        .isInstanceOf(ProtocolException.class);
  }

  @Test
  void refusesInvalidObjects() {
    // Status desconhecido.
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    try {
      out.writeBoolean(false);
      out.writeBoolean(false);
      out.writeBoolean(false);
      out.writeBoolean(false);
      Protocol.writeString(out, "EXPLODED");
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    assertThatThrownBy(() -> Protocol.readResult(input(bytes.toByteArray()), LIMITS))
        .isInstanceOf(ProtocolException.class)
        .hasMessageContaining("EXPLODED");
  }

  @Test
  void neverFailsWithAnythingButIoExceptionOnGarbage() {
    Random random = new Random(16);
    for (int i = 0; i < 20_000; i++) {
      byte[] garbage = new byte[random.nextInt(64)];
      random.nextBytes(garbage);
      try {
        Protocol.readResult(input(garbage), LIMITS);
      } catch (ProtocolException | EOFException e) {
        // esperado
      } catch (IOException e) {
        throw new AssertionError("Unexpected IOException type", e);
      }
    }
  }

  @Test
  void neverFailsWithAnythingButIoExceptionOnMutatedResults() throws IOException {
    byte[] valid =
        encode(
            new MetadataResult(
                FileInfo.of(Optional.of("a.jpg"), OptionalLong.of(3)),
                Optional.empty(),
                ExtractionStatus.SUCCESS,
                List.of(
                    new Metadata(
                        Optional.empty(),
                        new ListValue(List.of(MetadataValue.of("x"), MetadataValue.of(1))),
                        "a",
                        "N",
                        "p",
                        false)),
                List.of(Warning.of(WarningCode.CANCELLED, "m")),
                Duration.ZERO));
    Random random = new Random(42);
    for (int i = 0; i < 20_000; i++) {
      byte[] mutated = valid.clone();
      for (int j = 0; j < 1 + random.nextInt(4); j++) {
        mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
      }
      try {
        Protocol.readResult(input(mutated), LIMITS);
      } catch (ProtocolException | EOFException e) {
        // esperado
      }
    }
  }

  private static MetadataResult roundTrip(MetadataResult result) throws IOException {
    DataInputStream in = input(encode(result));
    MetadataResult decoded = Protocol.readResult(in, LIMITS);
    assertThat(in.read()).isEqualTo(-1);
    return decoded;
  }

  private static byte[] encode(MetadataResult result) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Protocol.writeResult(new DataOutputStream(bytes), result);
    return bytes.toByteArray();
  }

  private static DataInputStream input(byte[] bytes) {
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  private static <T> List<T> concat(List<T> a, List<T> b) {
    return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
  }
}
