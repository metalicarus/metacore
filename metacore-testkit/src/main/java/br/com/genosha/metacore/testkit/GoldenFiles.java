package br.com.genosha.metacore.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Comparação com arquivos esperados em {@code src/test/resources/golden}. Para atualizar depois de
 * uma mudança intencional: {@code -Dmetacore.updateGolden=true}.
 */
public final class GoldenFiles {

  private static final Path ROOT = Path.of("src/test/resources/golden");

  private GoldenFiles() {}

  public static void assertMatches(String group, String name, String actual) {
    Path golden = ROOT.resolve(group).resolve(name);
    try {
      if (Boolean.getBoolean("metacore.updateGolden")) {
        Files.createDirectories(golden.getParent());
        Files.writeString(golden, actual, StandardCharsets.UTF_8);
      }
      assertThat(golden).as("golden file; run with -Dmetacore.updateGolden=true").exists();
      assertThat(actual).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
