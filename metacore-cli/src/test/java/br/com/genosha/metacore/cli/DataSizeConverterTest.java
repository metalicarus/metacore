package br.com.genosha.metacore.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import picocli.CommandLine.TypeConversionException;

class DataSizeConverterTest {

  private final DataSizeConverter converter = new DataSizeConverter();

  @Test
  void acceptsBinaryUnits() {
    assertThat(converter.convert("512")).isEqualTo(512L);
    assertThat(converter.convert("64k")).isEqualTo(64L * 1024);
    assertThat(converter.convert("200M")).isEqualTo(200L * 1024 * 1024);
    assertThat(converter.convert("1GB")).isEqualTo(1L << 30);
  }

  @Test
  void rejectsInvalidSizes() {
    assertThatThrownBy(() -> converter.convert("0")).isInstanceOf(TypeConversionException.class);
    assertThatThrownBy(() -> converter.convert("-1")).isInstanceOf(TypeConversionException.class);
    assertThatThrownBy(() -> converter.convert("1T")).isInstanceOf(TypeConversionException.class);
    assertThatThrownBy(() -> converter.convert("99999999999999999999"))
        .isInstanceOf(TypeConversionException.class);
  }

  @Test
  void worstExitCodeWins() {
    assertThat(ExitCodes.worst(ExitCodes.OK, ExitCodes.STRICT)).isEqualTo(ExitCodes.STRICT);
    assertThat(ExitCodes.worst(ExitCodes.FAILURE, ExitCodes.STRICT)).isEqualTo(ExitCodes.FAILURE);
    assertThat(ExitCodes.worst(ExitCodes.STRICT, ExitCodes.OK)).isEqualTo(ExitCodes.STRICT);
  }
}
