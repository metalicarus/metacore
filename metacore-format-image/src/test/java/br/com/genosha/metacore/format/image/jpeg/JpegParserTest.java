package br.com.genosha.metacore.format.image.jpeg;

import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_BIT_DEPTH;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_CAMERA_EXPOSURE_TIME;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_CAMERA_MAKE;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_CAMERA_MODEL;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_DATETIME_ORIGINAL;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_HEIGHT;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_ORIENTATION;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_THUMBNAIL_HEIGHT;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_THUMBNAIL_WIDTH;
import static br.com.genosha.metacore.metadata.MetadataKeys.IMAGE_WIDTH;
import static br.com.genosha.metacore.metadata.MetadataKeys.LOCATION_LATITUDE;
import static br.com.genosha.metacore.metadata.MetadataKeys.LOCATION_LONGITUDE;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.metadata.DecimalValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JpegParserTest {

  private static final MetadataAnalyzer ANALYZER = MetadataAnalyzer.createDefault();

  @Test
  void readsTheImageStructureWithoutExif() {
    MetadataResult result = analyze(JpegFixtures.plain());

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(value(result, IMAGE_WIDTH)).isEqualTo(MetadataValue.of(JpegFixtures.WIDTH));
    assertThat(value(result, IMAGE_HEIGHT)).isEqualTo(MetadataValue.of(JpegFixtures.HEIGHT));
    assertThat(value(result, IMAGE_BIT_DEPTH)).isEqualTo(MetadataValue.of(8));
    assertThat(result.find(IMAGE_CAMERA_MAKE)).isEmpty();
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void readsBasicExifWithTypedValues() {
    MetadataResult result = analyze(JpegFixtures.basicExif());

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(value(result, IMAGE_CAMERA_MAKE)).isEqualTo(MetadataValue.of("Genosha"));
    assertThat(value(result, IMAGE_CAMERA_MODEL)).isEqualTo(MetadataValue.of("MC-1"));
    assertThat(value(result, IMAGE_ORIENTATION)).isEqualTo(MetadataValue.of(6));
    assertThat(value(result, IMAGE_CAMERA_EXPOSURE_TIME)).isEqualTo(MetadataValue.rational(1, 125));
    assertThat(value(result, IMAGE_DATETIME_ORIGINAL))
        .isEqualTo(
            MetadataValue.of(OffsetDateTime.of(2026, 7, 14, 16, 32, 5, 0, ZoneOffset.ofHours(-3))));
    assertThat(result.find(IMAGE_CAMERA_MAKE).orElseThrow())
        .extracting(Metadata::namespace, Metadata::rawName, Metadata::source)
        .containsExactly("EXIF:IFD0", "Make", "jpeg");
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void prefersTheJpegStructureForDimensions() {
    MetadataResult result = analyze(JpegFixtures.basicExif());

    assertThat(result.find(IMAGE_WIDTH).orElseThrow().namespace()).isEqualTo("JPEG");
    assertThat(result.findAll(IMAGE_WIDTH))
        .extracting(Metadata::namespace)
        .containsExactly("JPEG", "EXIF:SubIFD");
  }

  @Test
  void keepsDatesWithoutOffsetAsLocal() {
    MetadataResult result = analyze(JpegFixtures.withoutOffset());

    assertThat(value(result, IMAGE_DATETIME_ORIGINAL))
        .isEqualTo(MetadataValue.of(LocalDateTime.of(2026, 7, 14, 16, 32, 5)));
  }

  @Test
  void convertsGpsToDecimalDegreesAndKeepsTheRawValues() {
    MetadataResult result = analyze(JpegFixtures.withGps());

    assertThat(((DecimalValue) value(result, LOCATION_LATITUDE)).value())
        .isEqualByComparingTo(new BigDecimal("-23.55"));
    assertThat(((DecimalValue) value(result, LOCATION_LONGITUDE)).value())
        .isEqualByComparingTo(new BigDecimal("-46.6333333"));
    assertThat(result.findRaw("EXIF:GPS", "GPS Latitude Ref"))
        .singleElement()
        .extracting(Metadata::value)
        .isEqualTo(MetadataValue.of("S"));
  }

  @Test
  void mapsThumbnailDimensionsToTheirOwnKeys() {
    MetadataResult result = analyze(JpegFixtures.withThumbnailDimensions());

    assertThat(value(result, IMAGE_THUMBNAIL_WIDTH)).isEqualTo(MetadataValue.of(160));
    assertThat(value(result, IMAGE_THUMBNAIL_HEIGHT)).isEqualTo(MetadataValue.of(120));
    assertThat(value(result, IMAGE_WIDTH)).isEqualTo(MetadataValue.of(JpegFixtures.WIDTH));
  }

  @Test
  void keepsUnconvertibleValuesAsRawFields() {
    MetadataResult result = analyze(JpegFixtures.withInvalidDate());

    assertThat(result.status()).isEqualTo(ExtractionStatus.SUCCESS);
    assertThat(result.find(IMAGE_DATETIME_ORIGINAL)).isEmpty();
    assertThat(result.findRaw("EXIF:SubIFD", "Date/Time Original"))
        .singleElement()
        .satisfies(m -> assertThat(m.key()).isEmpty());
    assertThat(result.warnings())
        .extracting(Warning::code)
        .containsExactly(WarningCode.VALUE_CONVERSION_FAILED);
  }

  @Test
  void readsXmpWhenThereIsNoExif() {
    MetadataResult result = analyze(JpegFixtures.xmpOnly());

    assertThat(value(result, IMAGE_CAMERA_MAKE)).isEqualTo(MetadataValue.of("Genosha"));
    assertThat(value(result, IMAGE_CAMERA_EXPOSURE_TIME)).isEqualTo(MetadataValue.rational(1, 250));
    assertThat(value(result, IMAGE_DATETIME_ORIGINAL))
        .isEqualTo(MetadataValue.of(OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)));
    assertThat(result.find(IMAGE_CAMERA_MAKE).orElseThrow().namespace()).isEqualTo("XMP");
  }

  @Test
  void prefersExifOverXmpAndReportsDivergence() {
    MetadataResult result = analyze(JpegFixtures.withConflictingXmp());

    assertThat(value(result, IMAGE_CAMERA_MAKE)).isEqualTo(MetadataValue.of("Genosha"));
    assertThat(result.findAll(IMAGE_CAMERA_MAKE)).hasSize(2);
    assertThat(result.warnings())
        .singleElement()
        .satisfies(
            w -> {
              assertThat(w.code()).isEqualTo(WarningCode.METADATA_CONFLICT);
              assertThat(w.location()).contains(IMAGE_CAMERA_MAKE);
            });
  }

  @Test
  void returnsWhatWasReadBeforeATruncation() {
    byte[] full = JpegFixtures.basicExif();
    MetadataResult result = analyze(JpegFixtures.truncate(full, full.length / 2));

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.find(IMAGE_CAMERA_MAKE)).isPresent();
    assertThat(result.warnings()).extracting(Warning::code).contains(WarningCode.TRUNCATED);
  }

  @Test
  void reportsTruncationRightAfterTheSignature() {
    MetadataResult result = analyze(JpegFixtures.truncate(JpegFixtures.plain(), 4));

    assertThat(result.status()).isEqualTo(ExtractionStatus.PARTIAL);
    assertThat(result.metadata()).isEmpty();
  }

  @Test
  void contentThatIsNotJpegIsNotIdentifiedAsJpeg() {
    MetadataResult result =
        ANALYZER.analyze(new ByteArrayInputStream("not an image".getBytes()), "photo.jpg");

    assertThat(result.status()).isEqualTo(ExtractionStatus.UNKNOWN_FORMAT);
  }

  private static MetadataResult analyze(byte[] content) {
    return ANALYZER.analyze(new ByteArrayInputStream(content), "photo.jpg");
  }

  private static MetadataValue value(MetadataResult result, String key) {
    return result.find(key).orElseThrow(() -> new AssertionError("missing " + key)).value();
  }
}
