package br.com.genosha.metacore.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Contrato que todo módulo de formato precisa cumprir (blueprint, seção 10.4). Estenda esta classe
 * nos testes do módulo e informe o provedor e os arquivos de teste.
 *
 * <pre>{@code
 * class ImageProviderContractTest extends MetadataParserContractTest {
 *   protected MetadataProvider provider() { return new ImageMetadataProvider(); }
 *   protected List<ContractFixture> fixtures() {
 *     return List.of(ContractFixture.of(Path.of("src/test/resources/basic.jpg"), "jpeg"));
 *   }
 * }
 * }</pre>
 */
public abstract class MetadataParserContractTest {

  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

  /** Tempo máximo de uma análise de arquivo truncado antes de o teste considerar travamento. */
  protected static final Duration TRUNCATED_TIMEOUT = Duration.ofSeconds(10);

  /** Provedor sob teste. */
  protected abstract MetadataProvider provider();

  /** Arquivos válidos, cobrindo cada formato que o provedor atende. */
  protected abstract List<ContractFixture> fixtures();

  /** Limites usados nas análises. */
  protected AnalysisLimits limits() {
    return AnalysisLimits.defaults();
  }

  @Test
  void providerBuildsAConsistentAnalyzer() {
    MetadataAnalyzer analyzer = analyzer();

    Set<String> declared =
        provider().formats().stream().map(FileFormat::id).collect(Collectors.toSet());
    for (MetadataParser parser : provider().parsers()) {
      assertThat(parser.supportedFormats())
          .as("formats of parser '%s'", parser.id())
          .isNotEmpty()
          .allSatisfy(id -> assertThat(analyzer.formats().find(id)).isPresent());
    }
    assertThat(declared).as("formats declared by the provider").isNotEmpty();
  }

  @Test
  void identifiersFollowTheConvention() {
    Stream.concat(
            provider().detectors().stream().map(FormatDetector::id),
            provider().parsers().stream().map(MetadataParser::id))
        .forEach(id -> assertThat(id).as("component id").matches(ID));
  }

  @TestFactory
  Stream<DynamicNode> fixturesHonourTheContract() {
    List<ContractFixture> fixtures = fixtures();
    assertThat(fixtures).as("fixtures").isNotEmpty();
    return fixtures.stream()
        .map(
            fixture ->
                DynamicContainer.dynamicContainer(
                    fixture.toString(),
                    Stream.of(
                        DynamicTest.dynamicTest(
                            "extracts metadata", () -> extractsMetadata(fixture)),
                        DynamicTest.dynamicTest(
                            "follows field conventions", () -> followsFieldConventions(fixture)),
                        DynamicTest.dynamicTest(
                            "does not modify the source", () -> doesNotModifySource(fixture)),
                        DynamicTest.dynamicTest("is deterministic", () -> isDeterministic(fixture)),
                        DynamicTest.dynamicTest(
                            "terminates on truncated input",
                            () -> terminatesOnTruncatedInput(fixture)))));
  }

  private void extractsMetadata(ContractFixture fixture) {
    MetadataResult result = analyzer().analyze(fixture.file());

    assertThat(result.format()).map(FileFormat::id).contains(fixture.formatId());
    assertThat(result.status())
        .as("status, with warnings %s", result.warnings())
        .isIn(ExtractionStatus.SUCCESS, ExtractionStatus.PARTIAL);
    assertThat(result.metadata()).as("metadata").isNotEmpty();
  }

  private void followsFieldConventions(ContractFixture fixture) {
    MetadataResult result = analyzer().analyze(fixture.file());
    Set<String> parserIds =
        provider().parsers().stream().map(MetadataParser::id).collect(Collectors.toSet());

    for (Metadata metadata : result.metadata()) {
      assertThat(metadata.rawName()).as("rawName of %s", metadata).isNotBlank();
      assertThat(metadata.namespace()).as("namespace of %s", metadata).isNotBlank();
      assertThat(parserIds).as("source of %s", metadata).contains(metadata.source());
    }
    Map<String, Long> primaries =
        result.metadata().stream()
            .filter(Metadata::primary)
            .collect(Collectors.groupingBy(m -> m.key().orElseThrow(), Collectors.counting()));
    Set<String> keys = new TreeSet<>();
    result.metadata().forEach(m -> m.key().ifPresent(keys::add));
    for (String key : keys) {
      assertThat(primaries.get(key)).as("primary values of '%s'", key).isEqualTo(1L);
    }
  }

  private void doesNotModifySource(ContractFixture fixture) throws IOException {
    byte[] before = sha256(Files.readAllBytes(fixture.file()));
    analyzer().analyze(fixture.file());
    byte[] after = sha256(Files.readAllBytes(fixture.file()));

    assertThat(after).as("SHA-256 of the source").isEqualTo(before);
  }

  private void isDeterministic(ContractFixture fixture) {
    MetadataResult first = analyzer().analyze(fixture.file());
    MetadataResult second = analyzer().analyze(fixture.file());

    assertThat(second.status()).isEqualTo(first.status());
    assertThat(second.metadata()).isEqualTo(first.metadata());
    assertThat(second.warnings()).isEqualTo(first.warnings());
  }

  private void terminatesOnTruncatedInput(ContractFixture fixture) throws IOException {
    byte[] content = Files.readAllBytes(fixture.file());
    String name = fixture.file().getFileName().toString();
    MetadataAnalyzer analyzer = analyzer();
    Set<Integer> cuts = new HashSet<>();
    for (Function<Integer, Integer> cut :
        List.<Function<Integer, Integer>>of(
            n -> 0, n -> 1, n -> n / 4, n -> n / 2, n -> n * 3 / 4, n -> n - 1)) {
      cuts.add(Math.max(0, Math.min(content.length, cut.apply(content.length))));
    }
    for (int cut : new TreeSet<>(cuts)) {
      byte[] truncated = java.util.Arrays.copyOf(content, cut);
      MetadataResult result =
          assertTimeoutPreemptively(
              TRUNCATED_TIMEOUT,
              () -> analyzer.analyze(new ByteArrayInputStream(truncated), name),
              () -> "analysis of " + name + " truncated to " + cut + " bytes did not finish");
      if (result == null) {
        fail("analysis of %s truncated to %d bytes returned null", name, cut);
      }
    }
  }

  private MetadataAnalyzer analyzer() {
    return MetadataAnalyzer.builder()
        .discoverProviders(false)
        .limits(limits())
        .addProvider(provider())
        .build();
  }

  private static byte[] sha256(byte[] content) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(content);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }
}
