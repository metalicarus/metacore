package br.com.genosha.metacore.format.image.png;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;

/**
 * Alvo de fuzzing com Jazzer: {@code JAZZER_FUZZ=1 mvn test -pl metacore-format-image -am
 * -Dtest=PngFuzzTest -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class PngFuzzTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();
  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  @FuzzTest(maxDuration = "60s")
  void analyzeNeverThrows(byte[] data) {
    byte[] png = new byte[data.length + SIGNATURE.length];
    System.arraycopy(SIGNATURE, 0, png, 0, SIGNATURE.length);
    System.arraycopy(data, 0, png, SIGNATURE.length, data.length);
    ANALYZER.analyze(new ByteArrayInputStream(png), "fuzz.png");
  }
}
