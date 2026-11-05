package br.com.genosha.metacore.format.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Mutações e cortes reproduzíveis (semente fixa) das fixtures. */
class OfficeRobustnessTest {

  private static final long SEED = 0x6f6f786dL;
  private static final int ROUNDS = 400;

  @Test
  void mutatedInputsNeverEscapeAsExceptions() {
    MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
    byte[][] seeds = {
      OfficeFixtures.docx(),
      OfficeFixtures.xlsx(),
      OfficeFixtures.docxWithDescriptors(),
      OfficeFixtures.plainZip(),
      OfficeFixtures.libreOffice("metadados.docx")
    };
    Random random = new Random(SEED);

    for (int round = 0; round < ROUNDS; round++) {
      byte[] mutated = mutate(seeds[round % seeds.length], random);
      int current = round;
      MetadataResult result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(10),
              () -> analyzer.analyze(new ByteArrayInputStream(mutated), "fuzz.docx"),
              () -> "round " + current + " did not finish");
      assertThat(result).as("round %d", round).isNotNull();
    }
  }

  @Test
  void everyPrefixOfAPackageIsHandled() {
    MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
    byte[] docx = OfficeFixtures.docxWithDescriptors();
    for (int length = 0; length <= docx.length; length += 13) {
      byte[] prefix = Arrays.copyOf(docx, length);
      assertThat(analyzer.analyze(new ByteArrayInputStream(prefix), "cut.docx"))
          .as("length %d", length)
          .isNotNull();
    }
  }

  private static byte[] mutate(byte[] seed, Random random) {
    byte[] copy = seed.clone();
    int changes = 1 + random.nextInt(8);
    for (int i = 0; i < changes; i++) {
      int position = 4 + random.nextInt(copy.length - 4);
      copy[position] = (byte) random.nextInt(256);
    }
    if (random.nextInt(4) == 0) {
      return Arrays.copyOf(copy, 4 + random.nextInt(copy.length - 4));
    }
    return copy;
  }
}
