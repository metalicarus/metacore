package br.com.genosha.metacore.cli;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.metadata.MetadataValue;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ValuesTest {

  @Test
  void neutralizesTerminalControlCharacters() {
    assertThat(Values.safe("ok")).isSameAs("ok");
    assertThat(Values.safe("a\u001b[2Jb")).isEqualTo("a\\u001b[2Jb");
    assertThat(Values.safe("x\ny\tz\u009b")).isEqualTo("x\\ny\\tz\\u009b");
    assertThat(Values.safe("abc‮txt.exe")).isEqualTo("abc\\u202etxt.exe");
    assertThat(Values.human(MetadataValue.of("evil\u0007"))).isEqualTo("evil\\u0007");
  }

  @Test
  void formatsValuesForPeople() {
    assertThat(
            Values.human(
                MetadataValue.of(
                    OffsetDateTime.of(2026, 7, 14, 16, 32, 5, 0, ZoneOffset.ofHours(-3)))))
        .isEqualTo("2026-07-14 16:32:05 -03:00");
    assertThat(Values.human(MetadataValue.of("x".repeat(200)))).hasSize(123).endsWith("...");
    assertThat(Values.size(OptionalLong.of(808))).isEqualTo("808 B");
    assertThat(Values.size(OptionalLong.of(1218))).isEqualTo("1.2 KiB");
    assertThat(Values.size(OptionalLong.of(300L << 20))).isEqualTo("300 MiB");
    assertThat(Values.size(OptionalLong.empty())).isEmpty();
    assertThat(Values.label("exposure_time")).isEqualTo("Exposure time");
  }

  @Test
  void csvFieldsAreQuotedAndCannotBecomeFormulas() {
    assertThat(CsvReport.field("plain")).isEqualTo("plain");
    assertThat(CsvReport.field("a,b")).isEqualTo("\"a,b\"");
    assertThat(CsvReport.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    assertThat(CsvReport.field("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    assertThat(CsvReport.field("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    assertThat(CsvReport.field("-23.55")).isEqualTo("-23.55");
    assertThat(CsvReport.field("+1/125")).isEqualTo("+1/125");
  }
}
