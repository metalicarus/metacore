package br.com.genosha.metacore.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.exception.AnalysisCancelledException;
import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.TextValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.testing.Fakes;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultParseContextTest {

  private static final DetectionResult DETECTION =
      new DetectionResult(Fakes.ALPHA, Confidence.EXACT, Set.of(Evidence.MAGIC), "alpha-magic");

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void discardsEntriesAboveTheLimitAndWarnsOnce() {
    DefaultParseContext context = context(AnalysisLimits.defaults().withMaxMetadataEntries(2));

    for (int i = 0; i < 5; i++) {
      context.emit(MetadataEntry.raw("NS", "Field" + i, MetadataValue.of(i)));
    }

    assertThat(context.entries()).hasSize(2);
    assertThat(context.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.LIMIT_REACHED);
  }

  @Test
  void truncatesLongTexts() {
    DefaultParseContext context = context(AnalysisLimits.defaults().withMaxValueLength(3));

    context.emit(MetadataEntry.raw("NS", "Comment", MetadataValue.of("abcdef")));

    assertThat(context.entries().get(0).value()).isEqualTo(new TextValue("abc"));
    assertThat(context.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.LIMIT_REACHED);
              assertThat(w.location()).contains("Comment");
            });
  }

  @Test
  void dropsLargeBinaryContentButKeepsTheSize() {
    DefaultParseContext context = context(AnalysisLimits.defaults().withMaxBinaryValueBytes(2));

    context.emit(MetadataEntry.raw("NS", "Thumbnail", BinaryValue.of(new byte[] {1, 2, 3})));
    context.emit(MetadataEntry.raw("NS", "Small", BinaryValue.of(new byte[] {1, 2})));

    assertThat(context.entries().get(0).value()).isEqualTo(BinaryValue.sizeOnly(3));
    assertThat(context.entries().get(1).value()).isEqualTo(BinaryValue.of(new byte[] {1, 2}));
    assertThat(context.warnings()).isEmpty();
  }

  @Test
  void appliesLimitsInsideLists() {
    DefaultParseContext context = context(AnalysisLimits.defaults().withMaxValueLength(2));

    context.emit(
        MetadataEntry.raw(
            "NS",
            "Keywords",
            new ListValue(List.of(MetadataValue.of("abc"), MetadataValue.of("d")))));

    assertThat(context.entries().get(0).value())
        .isEqualTo(new ListValue(List.of(MetadataValue.of("ab"), MetadataValue.of("d"))));
  }

  @Test
  void cancelsWhenTheThreadIsInterrupted() {
    DefaultParseContext context = context(AnalysisLimits.defaults());
    assertThatNoException().isThrownBy(context::checkCancelled);

    Thread.currentThread().interrupt();

    assertThatThrownBy(context::checkCancelled).isInstanceOf(AnalysisCancelledException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void cancelsAfterTheDeadline() {
    AnalysisLimits limits = AnalysisLimits.defaults().withMaxAnalysisTime(Duration.ofMillis(1));
    DefaultParseContext context =
        new DefaultParseContext(
            DETECTION, limits, System.nanoTime() - Duration.ofSeconds(1).toNanos());

    assertThatThrownBy(context::checkCancelled)
        .isInstanceOf(AnalysisCancelledException.class)
        .hasMessageContaining("maxAnalysisTime");
  }

  private static DefaultParseContext context(AnalysisLimits limits) {
    return new DefaultParseContext(DETECTION, limits, System.nanoTime());
  }
}
