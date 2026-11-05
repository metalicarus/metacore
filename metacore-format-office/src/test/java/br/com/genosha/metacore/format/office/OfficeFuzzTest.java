package br.com.genosha.metacore.format.office;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;

/**
 * Alvo de fuzzing com Jazzer: {@code JAZZER_FUZZ=1 mvn test -pl metacore-format-office -am
 * -Dtest=OfficeFuzzTest -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class OfficeFuzzTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  @FuzzTest(maxDuration = "60s")
  void analyzeNeverThrows(byte[] data) {
    byte[] zip = new byte[data.length + 4];
    zip[0] = 'P';
    zip[1] = 'K';
    zip[2] = 3;
    zip[3] = 4;
    System.arraycopy(data, 0, zip, 4, data.length);
    ANALYZER.analyze(new ByteArrayInputStream(zip), "fuzz.docx");
  }
}
