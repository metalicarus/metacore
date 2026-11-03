package br.com.genosha.metacore.format.image.jpeg;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.testkit.GoldenFiles;
import br.com.genosha.metacore.testkit.ResultRenderer;
import java.io.ByteArrayInputStream;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Compara a saída completa do parser com arquivos esperados versionados. */
class JpegGoldenTest {

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("plain", (Supplier<byte[]>) JpegFixtures::plain),
        Arguments.of("basic-exif", (Supplier<byte[]>) JpegFixtures::basicExif),
        Arguments.of("gps", (Supplier<byte[]>) JpegFixtures::withGps),
        Arguments.of(
            "thumbnail-dimensions", (Supplier<byte[]>) JpegFixtures::withThumbnailDimensions),
        Arguments.of("xmp-only", (Supplier<byte[]>) JpegFixtures::xmpOnly),
        Arguments.of("conflicting-xmp", (Supplier<byte[]>) JpegFixtures::withConflictingXmp),
        Arguments.of("invalid-date", (Supplier<byte[]>) JpegFixtures::withInvalidDate));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void matchesTheGoldenFile(String name, Supplier<byte[]> content) {
    String actual =
        ResultRenderer.render(
            MetadataAnalyzer.createDefault()
                .analyze(new ByteArrayInputStream(content.get()), name + ".jpg"));
    GoldenFiles.assertMatches("jpeg", name + ".txt", actual);
  }
}
