package br.com.genosha.metacore.format.image.internal;

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

import br.com.genosha.metacore.format.image.internal.Converters.TagConverter;
import br.com.genosha.metacore.metadata.MetadataValue;
import com.drew.metadata.Directory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Mapeamento de campos para chaves normalizadas, com a precedência de cada fonte (menor vence). A
 * tabela documentada fica em {@code docs/keys.md}; as duas precisam andar juntas.
 */
final class KeyMappings {

  record TagMapping(String key, int precedence, TagConverter converter) {}

  record XmpMapping(
      String key, int precedence, Function<String, Optional<MetadataValue>> converter) {}

  // Precedência: 1 = estrutura do arquivo ou fonte principal; números maiores = fontes secundárias.
  private static final Map<Class<? extends Directory>, Map<Integer, TagMapping>> TAGS =
      Map.of(
          JpegDirectory.class,
          Map.of(
              JpegDirectory.TAG_IMAGE_WIDTH, new TagMapping(IMAGE_WIDTH, 1, Converters.INTEGER),
              JpegDirectory.TAG_IMAGE_HEIGHT, new TagMapping(IMAGE_HEIGHT, 1, Converters.INTEGER),
              JpegDirectory.TAG_DATA_PRECISION,
                  new TagMapping(IMAGE_BIT_DEPTH, 1, Converters.INTEGER)),
          ExifIFD0Directory.class,
          Map.of(
              ExifDirectoryBase.TAG_IMAGE_WIDTH, new TagMapping(IMAGE_WIDTH, 3, Converters.INTEGER),
              ExifDirectoryBase.TAG_IMAGE_HEIGHT,
                  new TagMapping(IMAGE_HEIGHT, 3, Converters.INTEGER),
              ExifDirectoryBase.TAG_MAKE, new TagMapping(IMAGE_CAMERA_MAKE, 1, Converters.TEXT),
              ExifDirectoryBase.TAG_MODEL, new TagMapping(IMAGE_CAMERA_MODEL, 1, Converters.TEXT),
              ExifDirectoryBase.TAG_ORIENTATION,
                  new TagMapping(IMAGE_ORIENTATION, 1, Converters.INTEGER)),
          ExifSubIFDDirectory.class,
          Map.of(
              ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH,
                  new TagMapping(IMAGE_WIDTH, 2, Converters.INTEGER),
              ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT,
                  new TagMapping(IMAGE_HEIGHT, 2, Converters.INTEGER),
              ExifDirectoryBase.TAG_EXPOSURE_TIME,
                  new TagMapping(IMAGE_CAMERA_EXPOSURE_TIME, 1, Converters.RATIONAL),
              ExifDirectoryBase.TAG_DATETIME_ORIGINAL,
                  new TagMapping(
                      IMAGE_DATETIME_ORIGINAL,
                      1,
                      Converters.exifDate(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL))),
          ExifThumbnailDirectory.class,
          Map.of(
              ExifDirectoryBase.TAG_IMAGE_WIDTH,
                  new TagMapping(IMAGE_THUMBNAIL_WIDTH, 1, Converters.INTEGER),
              ExifDirectoryBase.TAG_IMAGE_HEIGHT,
                  new TagMapping(IMAGE_THUMBNAIL_HEIGHT, 1, Converters.INTEGER)));

  private static final Map<String, XmpMapping> XMP =
      Map.of(
          "tiff:Make", new XmpMapping(IMAGE_CAMERA_MAKE, 2, Converters.XMP_TEXT),
          "tiff:Model", new XmpMapping(IMAGE_CAMERA_MODEL, 2, Converters.XMP_TEXT),
          "tiff:Orientation", new XmpMapping(IMAGE_ORIENTATION, 2, Converters.XMP_INTEGER),
          "exif:ExposureTime",
              new XmpMapping(IMAGE_CAMERA_EXPOSURE_TIME, 2, Converters.XMP_RATIONAL),
          "exif:DateTimeOriginal", new XmpMapping(IMAGE_DATETIME_ORIGINAL, 2, Converters.XMP_DATE),
          "xmp:CreateDate", new XmpMapping(IMAGE_DATETIME_ORIGINAL, 3, Converters.XMP_DATE),
          "exif:PixelXDimension", new XmpMapping(IMAGE_WIDTH, 4, Converters.XMP_INTEGER),
          "exif:PixelYDimension", new XmpMapping(IMAGE_HEIGHT, 4, Converters.XMP_INTEGER));

  private KeyMappings() {}

  static Map<Integer, TagMapping> forDirectory(Class<? extends Directory> type) {
    return TAGS.getOrDefault(type, Map.of());
  }

  static Optional<XmpMapping> forXmpProperty(String path) {
    return Optional.ofNullable(XMP.get(path));
  }
}
