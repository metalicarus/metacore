package br.com.genosha.metacore.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class FileFormatTest {

  @Test
  void createsFormatWithoutParent() {
    FileFormat jpeg = FileFormat.of("jpeg", "image/jpeg", "JPEG", "jpg", "jpeg");

    assertThat(jpeg.parent()).isEmpty();
    assertThat(jpeg.extensions()).containsExactly("jpg", "jpeg");
  }

  @Test
  void withParentKeepsOtherFields() {
    FileFormat docx = FileFormat.of("docx", "application/x-docx", "DOCX", "docx").withParent("zip");

    assertThat(docx.parent()).contains("zip");
    assertThat(docx.id()).isEqualTo("docx");
  }

  @Test
  void matchesExtensionIgnoringCase() {
    FileFormat jpeg = FileFormat.of("jpeg", "image/jpeg", "JPEG", "jpg");

    assertThat(jpeg.hasExtension("JPG")).isTrue();
    assertThat(jpeg.hasExtension("png")).isFalse();
  }

  @Test
  void rejectsInvalidIdentifiers() {
    assertThatIllegalArgumentException().isThrownBy(() -> FileFormat.of("JPEG", "image/jpeg", "J"));
    assertThatIllegalArgumentException().isThrownBy(() -> FileFormat.of("", "image/jpeg", "J"));
  }

  @Test
  void rejectsExtensionWithDotOrUppercase() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileFormat.of("jpeg", "image/jpeg", "JPEG", ".jpg"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileFormat.of("jpeg", "image/jpeg", "JPEG", "JPG"));
  }

  @Test
  void rejectsBlankTexts() {
    assertThatIllegalArgumentException().isThrownBy(() -> FileFormat.of("jpeg", " ", "JPEG"));
    assertThatIllegalArgumentException().isThrownBy(() -> FileFormat.of("jpeg", "image/jpeg", ""));
  }

  @Test
  void rejectsSelfParent() {
    FileFormat zip = FileFormat.of("zip", "application/zip", "ZIP");

    assertThatIllegalArgumentException().isThrownBy(() -> zip.withParent("zip"));
  }
}
