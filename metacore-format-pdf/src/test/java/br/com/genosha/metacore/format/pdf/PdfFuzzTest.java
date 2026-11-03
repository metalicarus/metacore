package br.com.genosha.metacore.format.pdf;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;

/**
 * Alvo de fuzzing com Jazzer: {@code JAZZER_FUZZ=1 mvn test -pl metacore-format-pdf -am
 * -Dtest=PdfFuzzTest -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class PdfFuzzTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();
  private static final byte[] HEADER =
      "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  @FuzzTest(maxDuration = "60s")
  void analyzeNeverThrows(byte[] data) {
    byte[] pdf = new byte[data.length + HEADER.length];
    System.arraycopy(HEADER, 0, pdf, 0, HEADER.length);
    System.arraycopy(data, 0, pdf, HEADER.length, data.length);
    ANALYZER.analyze(new ByteArrayInputStream(pdf), "fuzz.pdf");
  }
}
