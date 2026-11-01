package br.com.genosha.metacore.api;

import static br.com.genosha.metacore.testing.Fakes.ALPHA;
import static br.com.genosha.metacore.testing.Fakes.ALPHA_CONTENT;
import static br.com.genosha.metacore.testing.Fakes.DOCX;
import static br.com.genosha.metacore.testing.Fakes.UNKNOWN_CONTENT;
import static br.com.genosha.metacore.testing.Fakes.ZIP;
import static br.com.genosha.metacore.testing.Fakes.ZIP_CONTENT;
import static br.com.genosha.metacore.testing.Fakes.alphaDetector;
import static br.com.genosha.metacore.testing.Fakes.detector;
import static br.com.genosha.metacore.testing.Fakes.parser;
import static br.com.genosha.metacore.testing.Fakes.provider;
import static br.com.genosha.metacore.testing.Fakes.zipDetector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.exception.MetadataExtractionException;
import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataKeys;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.testing.Fakes.ParseBody;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataAnalyzerTest {

  @TempDir Path dir;

  private static final ParseBody WIDTHS =
      (source, context) -> {
        context.emit(
            MetadataEntry.normalized(
                    MetadataKeys.IMAGE_WIDTH, MetadataValue.of(4000), "EXIF:IFD0", "Image Width")
                .withPrecedence(2));
        context.emit(
            MetadataEntry.normalized(
                    MetadataKeys.IMAGE_WIDTH, MetadataValue.of(4000), "ALPHA", "Width")
                .withPrecedence(1));
        context.emit(MetadataEntry.raw("ALPHA", "Lens Model", MetadataValue.of("EF 24-70")));
      };

  @Test
  void extractsMetadataFromAFile() throws IOException {
    MetadataAnalyzer analyzer = analyzer(alphaProvider(WIDTHS));

    MetadataResult result = analyzer.analyze(write("photo.alp", ALPHA_CONTENT));

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.format()).contains(ALPHA);
    assertThat(result.detection().orElseThrow().evidence())
        .containsExactly(Evidence.MAGIC, Evidence.EXTENSION);
    assertThat(result.file())
        .isEqualTo(
            new FileInfo(Optional.of("photo.alp"), ALPHA_CONTENT.length, Optional.of("alp")));
    assertThat(result.metadata())
        .hasSize(3)
        .extracting(Metadata::source)
        .containsOnly("alpha-parser");
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void findsPrimaryAllAndRawValues() throws IOException {
    MetadataResult result = analyzer(alphaProvider(WIDTHS)).analyze(write("a.alp", ALPHA_CONTENT));

    assertThat(result.find(MetadataKeys.IMAGE_WIDTH))
        .hasValueSatisfying(m -> assertThat(m.namespace()).isEqualTo("ALPHA"));
    assertThat(result.findAll(MetadataKeys.IMAGE_WIDTH)).hasSize(2);
    assertThat(result.findRaw("ALPHA", "Lens Model"))
        .singleElement()
        .satisfies(m -> assertThat(m.key()).isEmpty());
    assertThat(result.find(MetadataKeys.IMAGE_HEIGHT)).isEmpty();
  }

  @Test
  void reportsUnknownFormat() throws IOException {
    MetadataResult result =
        analyzer(alphaProvider(WIDTHS)).analyze(write("x.bin", UNKNOWN_CONTENT));

    assertThat(result.status()).isEqualTo(ExtractionStatus.UNKNOWN_FORMAT);
    assertThat(result.detection()).isEmpty();
    assertThat(result.metadata()).isEmpty();
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void reportsUnsupportedFormat() throws IOException {
    MetadataProvider zipOnly = provider(List.of(ZIP), List.of(zipDetector()), List.of());

    MetadataResult result = analyzer(zipOnly).analyze(write("a.zip", ZIP_CONTENT));

    assertThat(result.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
    assertThat(result.format()).contains(ZIP);
    assertThat(result.warnings()).extracting(Warning::code).containsExactly(WarningCode.NO_PARSER);
  }

  @Test
  void fallsBackToTheParserOfASupertype() throws IOException {
    MetadataProvider provider =
        provider(
            List.of(ZIP, DOCX),
            List.of(
                zipDetector(),
                detector(
                    "docx-structure",
                    0,
                    r ->
                        r.startsWith('P', 'K', 3, 4)
                            ? Optional.of(
                                DetectionCandidate.of("docx", Confidence.HIGH, Evidence.STRUCTURE))
                            : Optional.empty())),
            List.of(parser("zip-parser", Set.of("zip"), WIDTHS)));

    MetadataResult result = analyzer(provider).analyze(write("report.docx", ZIP_CONTENT));

    assertThat(result.format()).contains(DOCX);
    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.metadata()).extracting(Metadata::source).containsOnly("zip-parser");
  }

  @Test
  void prefersTheParserWithHigherPriority() throws IOException {
    MetadataProvider provider =
        provider(
            List.of(ALPHA),
            List.of(alphaDetector()),
            List.of(
                parser("generic", 0, Set.of("alpha"), WIDTHS),
                parser("special", 10, Set.of("alpha"), WIDTHS)));

    MetadataResult result = analyzer(provider).analyze(write("a.alp", ALPHA_CONTENT));

    assertThat(result.metadata()).extracting(Metadata::source).containsOnly("special");
  }

  @Test
  void parserFailureKeepsWhatWasAlreadyEmitted() throws IOException {
    MetadataResult result =
        analyzer(
                alphaProvider(
                    (source, context) -> {
                      context.emit(MetadataEntry.raw("ALPHA", "First", MetadataValue.of(1)));
                      throw new IOException("unexpected end of segment");
                    }))
            .analyze(write("a.alp", ALPHA_CONTENT));

    assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
    assertThat(result.metadata()).hasSize(1);
    assertThat(result.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.PARSER_ERROR);
              assertThat(w.message())
                  .contains("alpha-parser", "IOException: unexpected end of segment");
            });
  }

  @Test
  void extractionExceptionAndStackOverflowBecomeFailedResults() throws IOException {
    Path file = write("a.alp", ALPHA_CONTENT);

    MetadataResult extraction =
        analyzer(
                alphaProvider(
                    (s, c) -> {
                      throw new MetadataExtractionException("bad structure");
                    }))
            .analyze(file);
    MetadataResult overflow =
        analyzer(
                alphaProvider(
                    (s, c) -> {
                      throw new StackOverflowError();
                    }))
            .analyze(file);

    assertThat(extraction.status()).isEqualTo(ExtractionStatus.FAILED);
    assertThat(overflow.status()).isEqualTo(ExtractionStatus.FAILED);
    assertThat(overflow.warnings())
        .singleElement()
        .satisfies(w -> assertThat(w.message()).endsWith("StackOverflowError"));
  }

  @Test
  void dataLossWarningsMakeTheResultPartial() throws IOException {
    MetadataResult partial =
        analyzer(alphaProvider((s, c) -> c.warn(WarningCode.TRUNCATED, "Missing EOI", "offset 7")))
            .analyze(write("a.alp", ALPHA_CONTENT));
    MetadataResult success =
        analyzer(
                alphaProvider(
                    (s, c) -> c.warn(WarningCode.VALUE_CONVERSION_FAILED, "Kept as text")))
            .analyze(write("b.alp", ALPHA_CONTENT));

    assertThat(partial.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(success.status()).isEqualTo(ExtractionStatus.SUCCESS);
  }

  @Test
  void cancellationKeepsCollectedMetadata() throws IOException {
    AnalysisLimits limits = AnalysisLimits.defaults().withMaxAnalysisTime(Duration.ofMillis(5));
    MetadataProvider provider =
        alphaProvider(
            (source, context) -> {
              context.emit(MetadataEntry.raw("ALPHA", "First", MetadataValue.of(1)));
              long giveUp = System.nanoTime() + Duration.ofSeconds(10).toNanos();
              while (System.nanoTime() < giveUp) {
                context.checkCancelled();
              }
            });

    MetadataResult result =
        MetadataAnalyzer.builder()
            .discoverProviders(false)
            .limits(limits)
            .addProvider(provider)
            .build()
            .analyze(write("a.alp", ALPHA_CONTENT));

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.metadata()).hasSize(1);
    assertThat(result.warnings()).extracting(Warning::code).containsExactly(WarningCode.CANCELLED);
  }

  @Test
  void reportsExtensionMismatchWithoutChangingTheStatus() throws IOException {
    MetadataResult result =
        analyzer(alphaProvider(WIDTHS)).analyze(write("photo.jpg", ALPHA_CONTENT));

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.EXTENSION_MISMATCH);
  }

  @Test
  void analyzesStreamsWithoutClosingThem() {
    CloseTrackingStream in = new CloseTrackingStream(new ByteArrayInputStream(ALPHA_CONTENT));

    MetadataResult result = analyzer(alphaProvider(WIDTHS)).analyze(in, "photo.alp");

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.file().name()).contains("photo.alp");
    assertThat(in.closed).isFalse();
  }

  @Test
  void streamNameIsOptional() {
    MetadataResult result =
        analyzer(alphaProvider(WIDTHS)).analyze(new ByteArrayInputStream(ALPHA_CONTENT), null);

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.file().name()).isEmpty();
    assertThat(result.file().extension()).isEmpty();
  }

  @Test
  void analyzesInputSources() throws IOException {
    MetadataAnalyzer analyzer = analyzer(alphaProvider(WIDTHS));

    MetadataResult fromBytes = analyzer.analyze(InputSource.of(ALPHA_CONTENT, "a.alp"));
    MetadataResult fromPath = analyzer.analyze(InputSource.of(write("b.alp", ALPHA_CONTENT)));

    assertThat(fromBytes.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(fromPath.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(fromPath.file().name()).contains("b.alp");
  }

  @Test
  void rejectsMissingFilesAndDirectories() {
    MetadataAnalyzer analyzer = analyzer(alphaProvider(WIDTHS));

    assertThatThrownBy(() -> analyzer.analyze(dir.resolve("missing.alp")))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not found");
    assertThatThrownBy(() -> analyzer.analyze(dir)).isInstanceOf(InvalidInputException.class);
  }

  @Test
  void rejectsInputsAboveMaxFileSize() throws IOException {
    MetadataAnalyzer analyzer =
        MetadataAnalyzer.builder()
            .discoverProviders(false)
            .limits(AnalysisLimits.defaults().withMaxFileSize(3))
            .addProvider(alphaProvider(WIDTHS))
            .build();
    Path file = write("a.alp", ALPHA_CONTENT);

    assertThatThrownBy(() -> analyzer.analyze(file))
        .isInstanceOf(InputLimitExceededException.class);
    assertThatThrownBy(() -> analyzer.analyze(new ByteArrayInputStream(ALPHA_CONTENT), "a.alp"))
        .isInstanceOf(InputLimitExceededException.class);
  }

  @Test
  void identifiesWithoutRunningTheParser() throws IOException {
    AtomicBoolean parsed = new AtomicBoolean();
    MetadataAnalyzer analyzer = analyzer(alphaProvider((s, c) -> parsed.set(true)));

    IdentificationResult result = analyzer.identify(write("a.alp", ALPHA_CONTENT));

    assertThat(result.format()).contains(ALPHA);
    assertThat(parsed).isFalse();
  }

  @Test
  void detectionOnlySeesTheConfiguredHeaderSize() throws IOException {
    MetadataProvider provider =
        provider(
            List.of(ALPHA),
            List.of(
                detector(
                    "length-check",
                    0,
                    r ->
                        r.length() == 2
                            ? Optional.of(
                                DetectionCandidate.of("alpha", Confidence.LOW, Evidence.MAGIC))
                            : Optional.empty())),
            List.of());
    MetadataAnalyzer analyzer =
        MetadataAnalyzer.builder()
            .discoverProviders(false)
            .limits(AnalysisLimits.defaults().withMaxDetectionBytes(2))
            .addProvider(provider)
            .build();

    assertThat(analyzer.identify(write("a.alp", ALPHA_CONTENT)).format()).contains(ALPHA);
  }

  @Test
  void discoversProvidersThroughServiceLoader() throws IOException {
    Path file = write("x.gam", DiscoverableTestProvider.GAMMA_CONTENT);

    assertThat(MetadataAnalyzer.createDefault().identify(file).format())
        .contains(DiscoverableTestProvider.GAMMA);
    assertThat(analyzer(alphaProvider(WIDTHS)).identify(file).format()).isEmpty();
  }

  @Test
  void explicitProviderIsNotRegisteredTwiceByDiscovery() {
    MetadataAnalyzer analyzer =
        MetadataAnalyzer.builder().addProvider(new DiscoverableTestProvider()).build();

    assertThat(analyzer.formats().find("gamma")).isPresent();
  }

  @Test
  void rejectsInconsistentProviders() {
    MetadataParser orphan = parser("orphan", Set.of("missing"), WIDTHS);

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                analyzer(
                    alphaProvider(WIDTHS),
                    provider(List.of(), List.of(alphaDetector()), List.of())))
        .withMessageContaining("alpha-magic");
    assertThatIllegalStateException()
        .isThrownBy(() -> analyzer(provider(List.of(ALPHA), List.of(), List.of(orphan))))
        .withMessageContaining("missing");
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                analyzer(
                    alphaProvider(WIDTHS),
                    provider(
                        List.of(FileFormat.of("alpha", "text/plain", "Other")),
                        List.of(),
                        List.of())))
        .withMessageContaining("alpha");
  }

  private static MetadataProvider alphaProvider(ParseBody body) {
    return provider(
        List.of(ALPHA),
        List.of(alphaDetector()),
        List.of(parser("alpha-parser", Set.of("alpha"), body)));
  }

  private static MetadataAnalyzer analyzer(MetadataProvider... providers) {
    MetadataAnalyzer.Builder builder = MetadataAnalyzer.builder().discoverProviders(false);
    for (MetadataProvider provider : providers) {
      builder.addProvider(provider);
    }
    return builder.build();
  }

  private Path write(String name, byte[] content) throws IOException {
    return Files.write(dir.resolve(name), content);
  }

  private static final class CloseTrackingStream extends FilterInputStream {
    boolean closed;

    CloseTrackingStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
