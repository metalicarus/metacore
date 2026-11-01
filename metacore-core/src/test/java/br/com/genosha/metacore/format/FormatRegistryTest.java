package br.com.genosha.metacore.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class FormatRegistryTest {

  private static final FileFormat ZIP = FileFormat.of("zip", "application/zip", "ZIP", "zip");
  private static final FileFormat OOXML =
      FileFormat.of("ooxml", "application/x-ooxml", "OOXML").withParent("zip");
  private static final FileFormat DOCX =
      FileFormat.of("docx", "application/x-docx", "DOCX", "docx").withParent("ooxml");

  @Test
  void lineageGoesFromMostSpecificToMostGeneric() {
    FormatRegistry registry = FormatRegistry.of(List.of(DOCX, ZIP, OOXML));

    assertThat(registry.lineage("docx"))
        .extracting(FileFormat::id)
        .containsExactly("docx", "ooxml", "zip");
    assertThat(registry.lineage("unknown")).isEmpty();
  }

  @Test
  void checksDescendants() {
    FormatRegistry registry = FormatRegistry.of(List.of(ZIP, OOXML, DOCX));

    assertThat(registry.isSameOrDescendant("docx", "zip")).isTrue();
    assertThat(registry.isSameOrDescendant("zip", "zip")).isTrue();
    assertThat(registry.isSameOrDescendant("zip", "docx")).isFalse();
  }

  @Test
  void acceptsIdenticalDuplicates() {
    FormatRegistry registry = FormatRegistry.of(List.of(ZIP, ZIP));

    assertThat(registry.all()).containsExactly(ZIP);
  }

  @Test
  void rejectsConflictingDefinitions() {
    FileFormat otherZip = FileFormat.of("zip", "application/x-zip", "Zip");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> FormatRegistry.of(List.of(ZIP, otherZip)))
        .withMessageContaining("zip");
  }

  @Test
  void rejectsUnknownParent() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FormatRegistry.of(List.of(DOCX, ZIP)))
        .withMessageContaining("ooxml");
  }

  @Test
  void rejectsCycles() {
    FileFormat a = FileFormat.of("a", "application/x-a", "A").withParent("b");
    FileFormat b = FileFormat.of("b", "application/x-b", "B").withParent("a");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> FormatRegistry.of(List.of(a, b)))
        .withMessageContaining("Cycle");
  }
}
