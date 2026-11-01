package br.com.genosha.metacore.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrimarySelectorTest {

  @Test
  void lowestPrecedenceIsPrimary() {
    List<MetadataEntry> entries =
        List.of(
            MetadataEntry.normalized(
                    "image.width", MetadataValue.of(4000), "EXIF:IFD0", "Image Width")
                .withPrecedence(3),
            MetadataEntry.normalized("image.width", MetadataValue.of(4000), "JPEG", "Image Width")
                .withPrecedence(1));

    PrimarySelector.Selection selection = PrimarySelector.select(entries, "fake");

    assertThat(selection.metadata()).extracting(Metadata::primary).containsExactly(false, true);
    assertThat(selection.metadata()).extracting(Metadata::source).containsOnly("fake");
    assertThat(selection.warnings()).isEmpty();
  }

  @Test
  void firstEmittedWinsOnEqualPrecedence() {
    List<MetadataEntry> entries =
        List.of(
            MetadataEntry.normalized("document.title", MetadataValue.of("A"), "PDF:Info", "Title"),
            MetadataEntry.normalized("document.title", MetadataValue.of("A"), "XMP:dc", "title"));

    PrimarySelector.Selection selection = PrimarySelector.select(entries, "fake");

    assertThat(selection.metadata()).extracting(Metadata::primary).containsExactly(true, false);
  }

  @Test
  void divergingValuesAreReportedOncePerKey() {
    List<MetadataEntry> entries =
        List.of(
            MetadataEntry.normalized("image.width", MetadataValue.of(4000), "JPEG", "Image Width"),
            MetadataEntry.normalized("image.width", MetadataValue.of(1000), "EXIF", "Width"),
            MetadataEntry.normalized("image.width", MetadataValue.of(999), "EXIF", "Other"));

    PrimarySelector.Selection selection = PrimarySelector.select(entries, "fake");

    assertThat(selection.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.METADATA_CONFLICT);
              assertThat(w.location()).contains("image.width");
            });
  }

  @Test
  void sameLocalTimeWithAndWithoutOffsetIsNotAConflict() {
    LocalDateTime local = LocalDateTime.of(2026, 7, 14, 16, 32, 5);
    List<MetadataEntry> entries =
        List.of(
            MetadataEntry.normalized(
                "image.datetime_original", MetadataValue.of(local), "EXIF", "DateTimeOriginal"),
            MetadataEntry.normalized(
                "image.datetime_original",
                MetadataValue.of(OffsetDateTime.of(local, ZoneOffset.ofHours(-3))),
                "XMP",
                "exif:DateTimeOriginal"));

    assertThat(PrimarySelector.select(entries, "fake").warnings()).isEmpty();
  }

  @Test
  void rawFieldsAreNeverPrimary() {
    List<MetadataEntry> entries =
        List.of(MetadataEntry.raw("EXIF:SubIFD", "Lens Model", MetadataValue.of("EF 24-70")));

    PrimarySelector.Selection selection = PrimarySelector.select(entries, "fake");

    assertThat(selection.metadata()).singleElement().extracting(Metadata::primary).isEqualTo(false);
    assertThat(selection.warnings()).extracting(Warning::code).isEmpty();
  }
}
