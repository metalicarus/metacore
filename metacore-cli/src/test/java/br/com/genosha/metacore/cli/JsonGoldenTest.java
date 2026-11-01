package br.com.genosha.metacore.cli;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Protege o contrato JSON: qualquer mudança na saída aparece no diff do golden file. Para atualizar
 * depois de uma mudança intencional: {@code -Dmetacore.updateGolden=true}.
 */
class JsonGoldenTest {

  private static final Path GOLDEN = Path.of("src/test/resources/golden/cli/inspect-gps.json");

  @TempDir Path dir;

  @Test
  void inspectJsonMatchesTheGoldenFile() throws IOException {
    Path photo = Files.write(dir.resolve("photo.jpg"), JpegFixtures.withGps());

    String actual = CliRun.run("inspect", "--output", "json", photo.toString()).out();

    if (Boolean.getBoolean("metacore.updateGolden")) {
      Files.createDirectories(GOLDEN.getParent());
      Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
    }
    assertThat(actual).isEqualTo(Files.readString(GOLDEN, StandardCharsets.UTF_8));
  }
}
