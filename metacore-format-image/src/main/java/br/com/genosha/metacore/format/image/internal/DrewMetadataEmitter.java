package br.com.genosha.metacore.format.image.internal;

import static br.com.genosha.metacore.metadata.MetadataKeys.LOCATION_LATITUDE;
import static br.com.genosha.metacore.metadata.MetadataKeys.LOCATION_LONGITUDE;

import br.com.genosha.metacore.format.image.internal.KeyMappings.TagMapping;
import br.com.genosha.metacore.format.image.internal.KeyMappings.XmpMapping;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.ParseContext;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Emite no {@link ParseContext} todos os campos de um {@link Metadata} do metadata-extractor:
 * campos brutos com valor tipado, chaves normalizadas quando há mapeamento, coordenadas GPS em
 * graus decimais e propriedades XMP. Erros registrados pela biblioteca viram avisos {@code
 * CORRUPTED_SEGMENT}.
 */
public final class DrewMetadataEmitter {

  static final String XMP_NAMESPACE = "XMP";
  private static final int COORDINATE_SCALE = 7;

  private DrewMetadataEmitter() {}

  public static void emit(Metadata metadata, ParseContext context) {
    for (Directory directory : metadata.getDirectories()) {
      context.checkCancelled();
      String namespace = namespace(directory);
      Map<Integer, TagMapping> mappings = KeyMappings.forDirectory(directory.getClass());
      for (Tag tag : directory.getTags()) {
        emitTag(directory, tag, namespace, mappings.get(tag.getTagType()), context);
      }
      if (directory instanceof GpsDirectory gps) {
        emitLocation(gps, namespace, context);
      }
      if (directory instanceof XmpDirectory xmp) {
        emitXmp(xmp, context);
      }
      for (String error : directory.getErrors()) {
        context.warn(WarningCode.CORRUPTED_SEGMENT, error, namespace);
      }
    }
  }

  /**
   * Namespace estável a partir do nome do diretório: {@code Exif SubIFD} vira {@code EXIF:SubIFD},
   * {@code Exif Thumbnail} vira {@code EXIF:IFD1} e {@code GPS} vira {@code EXIF:GPS}. Os demais
   * mantêm o nome do metadata-extractor.
   */
  static String namespace(Directory directory) {
    String name = directory.getName();
    return switch (name) {
      case "Exif Thumbnail" -> "EXIF:IFD1";
      case "GPS" -> "EXIF:GPS";
      case "Interoperability" -> "EXIF:Interop";
      default -> name.startsWith("Exif ") ? "EXIF:" + name.substring(5) : name;
    };
  }

  private static void emitTag(
      Directory directory, Tag tag, String namespace, TagMapping mapping, ParseContext context) {
    int type = tag.getTagType();
    String name = tag.getTagName();
    if (mapping != null) {
      Optional<MetadataValue> value = mapping.converter().convert(directory, type);
      if (value.isPresent()) {
        context.emit(
            new MetadataEntry(
                Optional.of(mapping.key()), value.get(), name, namespace, mapping.precedence()));
        return;
      }
      conversionFailed(context, namespace, name, mapping.key());
    }
    MetadataValue value =
        RawValues.convert(directory.getObject(type))
            .orElseGet(
                () ->
                    MetadataValue.of(
                        Objects.requireNonNullElse(directory.getDescription(type), "")));
    context.emit(MetadataEntry.raw(namespace, name, value));
  }

  private static void emitLocation(GpsDirectory gps, String namespace, ParseContext context) {
    boolean hasCoordinates =
        gps.containsTag(GpsDirectory.TAG_LATITUDE) || gps.containsTag(GpsDirectory.TAG_LONGITUDE);
    if (!hasCoordinates) {
      return;
    }
    GeoLocation location = gps.getGeoLocation();
    if (location == null) {
      conversionFailed(context, namespace, "GPS Latitude/Longitude", LOCATION_LATITUDE);
      return;
    }
    context.emit(
        new MetadataEntry(
            Optional.of(LOCATION_LATITUDE),
            coordinate(location.getLatitude()),
            "GPS Latitude",
            namespace,
            1));
    context.emit(
        new MetadataEntry(
            Optional.of(LOCATION_LONGITUDE),
            coordinate(location.getLongitude()),
            "GPS Longitude",
            namespace,
            1));
  }

  private static MetadataValue coordinate(double degrees) {
    return MetadataValue.of(
        BigDecimal.valueOf(degrees).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
  }

  private static void emitXmp(XmpDirectory xmp, ParseContext context) {
    Map<String, String> properties = new TreeMap<>(xmp.getXmpProperties());
    for (Map.Entry<String, String> property : properties.entrySet()) {
      String path = property.getKey();
      String text = property.getValue();
      Optional<XmpMapping> mapping = KeyMappings.forXmpProperty(path);
      if (mapping.isPresent()) {
        Optional<MetadataValue> value = mapping.get().converter().apply(text);
        if (value.isPresent()) {
          context.emit(
              new MetadataEntry(
                  Optional.of(mapping.get().key()),
                  value.get(),
                  path,
                  XMP_NAMESPACE,
                  mapping.get().precedence()));
          continue;
        }
        conversionFailed(context, XMP_NAMESPACE, path, mapping.get().key());
      }
      context.emit(MetadataEntry.raw(XMP_NAMESPACE, path, MetadataValue.of(text)));
    }
  }

  private static void conversionFailed(
      ParseContext context, String namespace, String rawName, String key) {
    context.warn(
        WarningCode.VALUE_CONVERSION_FAILED,
        "Could not convert '" + rawName + "' to '" + key + "'; kept as a raw field",
        namespace + "/" + rawName);
  }
}
