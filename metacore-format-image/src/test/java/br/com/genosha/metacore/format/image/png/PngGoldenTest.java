package br.com.genosha.metacore.format.image.png;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.format.image.fixtures.PngFixtures;
import br.com.genosha.metacore.testkit.GoldenFiles;
import br.com.genosha.metacore.testkit.ResultRenderer;
import java.io.ByteArrayInputStream;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Compara a saída completa do parser com arquivos esperados versionados. */
class PngGoldenTest {

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("plain", (Supplier<byte[]>) PngFixtures::plain),
        Arguments.of("text", (Supplier<byte[]>) PngFixtures::withText),
        Arguments.of("text-after-image-data", (Supplier<byte[]>) PngFixtures::textAfterImageData),
        Arguments.of("exif", (Supplier<byte[]>) PngFixtures::withExif),
        Arguments.of("xmp", (Supplier<byte[]>) PngFixtures::withXmp));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void matchesTheGoldenFile(String name, Supplier<byte[]> content) {
    String actual =
        ResultRenderer.render(
            MetadataAnalyzer.createDefault()
                .analyze(new ByteArrayInputStream(content.get()), name + ".png"));
    GoldenFiles.assertMatches("png", name + ".txt", actual);
  }
}
