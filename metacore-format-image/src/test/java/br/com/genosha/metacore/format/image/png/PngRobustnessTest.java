package br.com.genosha.metacore.format.image.png;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.image.fixtures.PngFixtures;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Mutações reproduzíveis (semente fixa) das fixtures PNG. */
class PngRobustnessTest {

  private static final long SEED = 0x706E67L;
  private static final int ROUNDS = 300;

  @Test
  void mutatedInputsNeverEscapeAsExceptions() {
    MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
    byte[][] seeds = {PngFixtures.withText(), PngFixtures.withExif(), PngFixtures.withXmp()};
    Random random = new Random(SEED);

    for (int round = 0; round < ROUNDS; round++) {
      byte[] mutated = mutate(seeds[round % seeds.length], random);
      int current = round;
      MetadataResult result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(5),
              () -> analyzer.analyze(new ByteArrayInputStream(mutated), "fuzz.png"),
              () -> "round " + current + " did not finish");
      assertThat(result).as("round %d", round).isNotNull();
    }
  }

  private static byte[] mutate(byte[] seed, Random random) {
    byte[] copy = seed.clone();
    int changes = 1 + random.nextInt(8);
    for (int i = 0; i < changes; i++) {
      int position = 8 + random.nextInt(Math.min(copy.length - 8, 1500));
      copy[position] = (byte) random.nextInt(256);
    }
    if (random.nextInt(4) == 0) {
      return PngFixtures.truncate(copy, 8 + random.nextInt(copy.length - 8));
    }
    return copy;
  }
}
