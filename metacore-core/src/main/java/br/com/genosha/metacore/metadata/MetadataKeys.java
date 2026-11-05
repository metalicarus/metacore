package br.com.genosha.metacore.metadata;

import java.util.regex.Pattern;

/**
 * Chaves normalizadas e a convenção que toda chave segue: segmentos em minúsculas separados por
 * ponto, cada um começando por letra e contendo letras, dígitos ou sublinhado. O vocabulário
 * completo está em {@code docs/keys.md}.
 */
public final class MetadataKeys {

  /** Versão do vocabulário de chaves, publicada como {@code keysVersion} no JSON. */
  public static final String VERSION = "1";

  public static final String FILE_MIME_TYPE = "file.mime_type";
  public static final String FILE_EXTENSION = "file.extension";

  public static final String IMAGE_WIDTH = "image.width";
  public static final String IMAGE_HEIGHT = "image.height";
  public static final String IMAGE_BIT_DEPTH = "image.bit_depth";
  public static final String IMAGE_COLOR_TYPE = "image.color_type";
  public static final String IMAGE_ORIENTATION = "image.orientation";
  public static final String IMAGE_THUMBNAIL_WIDTH = "image.thumbnail.width";
  public static final String IMAGE_THUMBNAIL_HEIGHT = "image.thumbnail.height";
  public static final String IMAGE_CAMERA_MAKE = "image.camera.make";
  public static final String IMAGE_CAMERA_MODEL = "image.camera.model";
  public static final String IMAGE_CAMERA_EXPOSURE_TIME = "image.camera.exposure_time";
  public static final String IMAGE_DATETIME_ORIGINAL = "image.datetime_original";

  public static final String LOCATION_LATITUDE = "location.latitude";
  public static final String LOCATION_LONGITUDE = "location.longitude";

  public static final String DOCUMENT_TITLE = "document.title";
  public static final String DOCUMENT_AUTHOR = "document.author";
  public static final String DOCUMENT_LAST_MODIFIED_BY = "document.last_modified_by";
  public static final String DOCUMENT_SUBJECT = "document.subject";
  public static final String DOCUMENT_KEYWORDS = "document.keywords";
  public static final String DOCUMENT_CREATOR = "document.creator";
  public static final String DOCUMENT_PRODUCER = "document.producer";
  public static final String DOCUMENT_CREATED = "document.created";
  public static final String DOCUMENT_MODIFIED = "document.modified";
  public static final String DOCUMENT_PAGE_COUNT = "document.page_count";
  public static final String DOCUMENT_FORMAT_VERSION = "document.format_version";

  private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");

  private MetadataKeys() {}

  /** Indica se a chave segue a convenção. */
  public static boolean isValid(String key) {
    return key != null && KEY.matcher(key).matches();
  }
}
