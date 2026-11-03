package br.com.genosha.metacore.format.pdf;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.testkit.GoldenFiles;
import br.com.genosha.metacore.testkit.ResultRenderer;
import java.io.ByteArrayInputStream;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Compara a saída completa do parser com arquivos esperados versionados. */
class PdfGoldenTest {

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("simple", (Supplier<byte[]>) PdfFixtures::simple),
        Arguments.of("info", (Supplier<byte[]>) PdfFixtures::withInfo),
        Arguments.of("info-and-xmp", (Supplier<byte[]>) PdfFixtures::infoAndXmp),
        Arguments.of("pdf20-info-and-xmp", (Supplier<byte[]>) PdfFixtures::pdf20InfoAndXmp),
        Arguments.of("user-password", (Supplier<byte[]>) PdfFixtures::userPassword),
        Arguments.of("owner-password-only", (Supplier<byte[]>) PdfFixtures::ownerPasswordOnly));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void matchesTheGoldenFile(String name, Supplier<byte[]> content) {
    String actual =
        ResultRenderer.render(
            MetadataAnalyzer.createDefault()
                .analyze(new ByteArrayInputStream(content.get()), name + ".pdf"));
    GoldenFiles.assertMatches("pdf", name + ".txt", actual);
  }
}
