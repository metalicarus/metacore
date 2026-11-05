package br.com.genosha.metacore.format.office;

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
class OfficeGoldenTest {

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("docx", "docx", (Supplier<byte[]>) OfficeFixtures::docx),
        Arguments.of("docm", "docm", (Supplier<byte[]>) OfficeFixtures::docm),
        Arguments.of("xlsx", "xlsx", (Supplier<byte[]>) OfficeFixtures::xlsx),
        Arguments.of("pptx", "pptx", (Supplier<byte[]>) OfficeFixtures::pptx),
        Arguments.of("minimal", "docx", (Supplier<byte[]>) OfficeFixtures::minimalDocx),
        Arguments.of("plain-zip", "zip", (Supplier<byte[]>) OfficeFixtures::plainZip),
        Arguments.of(
            "libreoffice-docx",
            "docx",
            (Supplier<byte[]>) () -> OfficeFixtures.libreOffice("metadados.docx")),
        Arguments.of(
            "libreoffice-xlsx",
            "xlsx",
            (Supplier<byte[]>) () -> OfficeFixtures.libreOffice("metadados.xlsx")),
        Arguments.of(
            "libreoffice-pptx",
            "pptx",
            (Supplier<byte[]>) () -> OfficeFixtures.libreOffice("metadados.pptx")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void matchesTheGoldenFile(String name, String extension, Supplier<byte[]> content) {
    String actual =
        ResultRenderer.render(
            MetadataAnalyzer.createDefault()
                .analyze(new ByteArrayInputStream(content.get()), name + "." + extension));
    GoldenFiles.assertMatches("office", name + ".txt", actual);
  }
}
