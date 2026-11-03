package br.com.genosha.metacore.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetadataValueTest {

  @Test
  void factoriesProduceTheExpectedTypes() {
    assertThat(MetadataValue.of("x").type()).isEqualTo(ValueType.TEXT);
    assertThat(MetadataValue.of(4000).type()).isEqualTo(ValueType.INTEGER);
    assertThat(MetadataValue.of(true).type()).isEqualTo(ValueType.BOOLEAN);
    assertThat(MetadataValue.of(new BigDecimal("-23.5")).type()).isEqualTo(ValueType.DECIMAL);
    assertThat(MetadataValue.of(LocalDateTime.of(2026, 7, 14, 16, 32)).type())
        .isEqualTo(ValueType.LOCAL_DATE_TIME);
    assertThat(MetadataValue.rational(1, 125).type()).isEqualTo(ValueType.RATIONAL);
  }

  @Test
  void valuesPrintAsThemselves() {
    assertThat(MetadataValue.of("Genosha")).hasToString("Genosha");
    assertThat(MetadataValue.of(4000)).hasToString("4000");
    assertThat(MetadataValue.of(new BigDecimal("-23.5500000"))).hasToString("-23.5500000");
    assertThat(MetadataValue.of(LocalDateTime.of(2026, 8, 1, 9, 0)))
        .hasToString("2026-08-01T09:00:00");
    assertThat(new ListValue(List.of(MetadataValue.of("a"), MetadataValue.of(1))))
        .hasToString("[a, 1]");
  }

  @Test
  void rationalKeepsTheOriginalFraction() {
    RationalValue exposure = MetadataValue.rational(2, 250);

    assertThat(exposure).hasToString("2/250");
    assertThat(exposure.toBigDecimal(MathContext.DECIMAL64)).isEqualByComparingTo("0.008");
  }

  @Test
  void rationalRejectsZeroDenominator() {
    assertThatIllegalArgumentException().isThrownBy(() -> MetadataValue.rational(0, 0));
  }

  @Test
  void binaryValueCopiesItsContent() {
    byte[] content = {1, 2, 3};
    BinaryValue value = BinaryValue.of(content);
    content[0] = 9;
    value.content().orElseThrow()[1] = 9;

    assertThat(value.content()).hasValueSatisfying(c -> assertThat(c).containsExactly(1, 2, 3));
    assertThat(value.size()).isEqualTo(3);
  }

  @Test
  void binaryValueEqualityUsesContent() {
    assertThat(BinaryValue.of(new byte[] {1, 2})).isEqualTo(BinaryValue.of(new byte[] {1, 2}));
    assertThat(BinaryValue.sizeOnly(2)).isNotEqualTo(BinaryValue.of(new byte[] {1, 2}));
    assertThat(BinaryValue.sizeOnly(2).hasContent()).isFalse();
  }

  @Test
  void listValueIsImmutable() {
    List<MetadataValue> values = new ArrayList<>(List.of(MetadataValue.of("a")));
    ListValue list = new ListValue(values);
    values.add(MetadataValue.of("b"));

    assertThat(list.values()).containsExactly(MetadataValue.of("a"));
  }

  @Test
  void validatesKeys() {
    assertThat(MetadataKeys.isValid("image.camera.exposure_time")).isTrue();
    assertThat(MetadataKeys.isValid("image")).isTrue();
    assertThat(MetadataKeys.isValid("Image.Width")).isFalse();
    assertThat(MetadataKeys.isValid("image..width")).isFalse();
    assertThat(MetadataKeys.isValid("image.2width")).isFalse();
    assertThat(MetadataKeys.isValid(null)).isFalse();
  }

  @Test
  void onlyNormalizedFieldsCanBePrimary() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new Metadata(Optional.empty(), MetadataValue.of("x"), "Raw", "NS", "p", true));
  }

  @Test
  void warningCodesFollowTheConvention() {
    assertThat(new WarningCode("CUSTOM_CODE", false)).hasToString("CUSTOM_CODE");
    assertThatIllegalArgumentException().isThrownBy(() -> new WarningCode("lowercase", false));
    assertThat(WarningCode.TRUNCATED.dataLoss()).isTrue();
    assertThat(WarningCode.EXTENSION_MISMATCH.dataLoss()).isFalse();
  }
}
