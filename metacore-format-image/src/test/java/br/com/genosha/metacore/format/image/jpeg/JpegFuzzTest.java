package br.com.genosha.metacore.format.image.jpeg;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;

/**
 * Alvo de fuzzing com Jazzer. Numa build normal roda só as entradas de regressão; para fuzzing de
 * verdade: {@code JAZZER_FUZZ=1 mvn test -pl metacore-format-image -Dtest=JpegFuzzTest}.
 *
 * <p>Toda exceção que escapar de {@code analyze} é um bug: a análise deve sempre devolver um
 * resultado (ADR 0006).
 */
class JpegFuzzTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  @FuzzTest(maxDuration = "60s")
  void analyzeNeverThrows(byte[] data) {
    byte[] jpeg = new byte[data.length + 2];
    jpeg[0] = (byte) 0xFF;
    jpeg[1] = (byte) 0xD8;
    System.arraycopy(data, 0, jpeg, 2, data.length);
    ANALYZER.analyze(new ByteArrayInputStream(jpeg), "fuzz.jpg");
  }
}
