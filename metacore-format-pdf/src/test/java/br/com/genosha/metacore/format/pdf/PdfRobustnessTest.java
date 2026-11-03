package br.com.genosha.metacore.format.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Mutações reproduzíveis (semente fixa) das fixtures PDF. */
class PdfRobustnessTest {

  private static final long SEED = 0x706466L;
  private static final int ROUNDS = 200;

  @Test
  void mutatedInputsNeverEscapeAsExceptions() {
    MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
    byte[][] seeds = {
      PdfFixtures.withInfo(), PdfFixtures.infoAndXmp(), PdfFixtures.ownerPasswordOnly()
    };
    Random random = new Random(SEED);

    for (int round = 0; round < ROUNDS; round++) {
      byte[] mutated = mutate(seeds[round % seeds.length], random);
      int current = round;
      MetadataResult result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(10),
              () -> analyzer.analyze(new ByteArrayInputStream(mutated), "fuzz.pdf"),
              () -> "round " + current + " did not finish");
      assertThat(result).as("round %d", round).isNotNull();
    }
  }

  private static byte[] mutate(byte[] seed, Random random) {
    byte[] copy = seed.clone();
    int changes = 1 + random.nextInt(8);
    for (int i = 0; i < changes; i++) {
      int position = 8 + random.nextInt(copy.length - 8);
      copy[position] = (byte) random.nextInt(256);
    }
    if (random.nextInt(4) == 0) {
      return java.util.Arrays.copyOf(copy, 8 + random.nextInt(copy.length - 8));
    }
    return copy;
  }
}
