package br.com.genosha.metacore.format.image.jpeg;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.format.image.fixtures.ResultRenderer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compara a saída completa do parser com arquivos esperados versionados. Para atualizar depois de
 * uma mudança intencional: {@code mvn test -pl metacore-format-image -Dmetacore.updateGolden=true}.
 */
class JpegGoldenTest {

  private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden/jpeg");
  private static final boolean UPDATE = Boolean.getBoolean("metacore.updateGolden");

  static Stream<Arguments> fixtures() {
    return Stream.of(
        fixture("plain", JpegFixtures::plain),
        fixture("basic-exif", JpegFixtures::basicExif),
        fixture("gps", JpegFixtures::withGps),
        fixture("thumbnail-dimensions", JpegFixtures::withThumbnailDimensions),
        fixture("xmp-only", JpegFixtures::xmpOnly),
        fixture("conflicting-xmp", JpegFixtures::withConflictingXmp),
        fixture("invalid-date", JpegFixtures::withInvalidDate));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void matchesTheGoldenFile(String name, Supplier<byte[]> content) throws IOException {
    String actual =
        ResultRenderer.render(
            MetadataAnalyzer.createDefault()
                .analyze(new ByteArrayInputStream(content.get()), name + ".jpg"));
    Path golden = GOLDEN_DIR.resolve(name + ".txt");

    if (UPDATE) {
      Files.createDirectories(GOLDEN_DIR);
      Files.writeString(golden, actual, StandardCharsets.UTF_8);
    }
    assertThat(golden).as("golden file; run with -Dmetacore.updateGolden=true").exists();
    assertThat(actual).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
  }

  private static Arguments fixture(String name, Supplier<byte[]> content) {
    return Arguments.of(name, content);
  }
}
