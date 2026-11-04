package br.com.genosha.metacore.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.TypeConversionException;

class DurationConverterTest {

  private final DurationConverter converter = new DurationConverter();

  @Test
  void acceptsUnits() {
    assertThat(converter.convert("500ms")).isEqualTo(Duration.ofMillis(500));
    assertThat(converter.convert("30")).isEqualTo(Duration.ofSeconds(30));
    assertThat(converter.convert("10s")).isEqualTo(Duration.ofSeconds(10));
    assertThat(converter.convert("2M")).isEqualTo(Duration.ofMinutes(2));
    assertThat(converter.convert("1h")).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void rejectsInvalidDurations() {
    assertThatThrownBy(() -> converter.convert("0s")).isInstanceOf(TypeConversionException.class);
    assertThatThrownBy(() -> converter.convert("-1s")).isInstanceOf(TypeConversionException.class);
    assertThatThrownBy(() -> converter.convert("1d")).isInstanceOf(TypeConversionException.class);
    assertThatThrownBy(() -> converter.convert("PT30S"))
        .isInstanceOf(TypeConversionException.class);
  }
}
