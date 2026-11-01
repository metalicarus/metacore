package br.com.genosha.metacore.format.image.jpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Mutações aleatórias, mas reproduzíveis (semente fixa), das fixtures. Roda em toda build; o
 * fuzzing guiado por cobertura fica no {@link JpegFuzzTest}.
 */
class JpegRobustnessTest {

  private static final long SEED = 0x6D657461L;
  private static final int ROUNDS = 300;

  @Test
  void mutatedInputsNeverEscapeAsExceptions() {
    MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
    byte[][] seeds = {JpegFixtures.basicExif(), JpegFixtures.withGps(), JpegFixtures.xmpOnly()};
    Random random = new Random(SEED);

    for (int round = 0; round < ROUNDS; round++) {
      byte[] mutated = mutate(seeds[round % seeds.length], random);
      int current = round;
      MetadataResult result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(5),
              () -> analyzer.analyze(new ByteArrayInputStream(mutated), "fuzz.jpg"),
              () -> "round " + current + " did not finish");
      assertThat(result).as("round %d", round).isNotNull();
    }
  }

  private static byte[] mutate(byte[] seed, Random random) {
    byte[] copy = seed.clone();
    int changes = 1 + random.nextInt(8);
    for (int i = 0; i < changes; i++) {
      int position = 2 + random.nextInt(Math.min(copy.length - 2, 700));
      copy[position] = (byte) random.nextInt(256);
    }
    if (random.nextInt(4) == 0) {
      return JpegFixtures.truncate(copy, 2 + random.nextInt(copy.length - 2));
    }
    return copy;
  }
}
