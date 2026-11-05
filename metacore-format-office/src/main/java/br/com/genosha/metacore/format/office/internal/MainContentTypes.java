package br.com.genosha.metacore.format.office.internal;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tipo do documento a partir do content type da parte principal, declarado em {@code
 * [Content_Types].xml}: {@code ...wordprocessingml.document.main+xml} é DOCX, {@code
 * ...ms-excel.sheet.macroEnabled.main+xml} é XLSX com macros, e assim por diante.
 */
public final class MainContentTypes {

  /** Identificador do formato ({@code docx}, {@code xlsx}, {@code pptx}). */
  public static Optional<String> formatId(String contentType) {
    String type = contentType.toLowerCase(Locale.ROOT);
    if (!isMain(type)) {
      return Optional.empty();
    }
    if (type.contains("wordprocessingml") || type.contains("ms-word")) {
      return Optional.of("docx");
    }
    if (type.contains("spreadsheetml") || type.contains("ms-excel")) {
      return Optional.of("xlsx");
    }
    if (type.contains("presentationml") || type.contains("ms-powerpoint")) {
      return Optional.of("pptx");
    }
    return Optional.empty();
  }

  /** Indica se o content type é o de uma parte principal com macros. */
  public static boolean macroEnabled(String contentType) {
    return contentType.toLowerCase(Locale.ROOT).contains("macroenabled");
  }

  private static final Pattern CONTENT_TYPE = Pattern.compile("ContentType\\s*=\\s*\"([^\"]+)\"");

  /**
   * Procura no texto, mesmo incompleto, o primeiro content type de parte principal. Usado pelo
   * detector, que pode ver só o começo de {@code [Content_Types].xml}.
   */
  public static Optional<String> findMain(String xml) {
    Matcher matcher = CONTENT_TYPE.matcher(xml);
    while (matcher.find()) {
      if (formatId(matcher.group(1)).isPresent()) {
        return Optional.of(matcher.group(1));
      }
    }
    return Optional.empty();
  }

  private static boolean isMain(String type) {
    return type.endsWith(".main+xml") || type.endsWith(".main");
  }

  private MainContentTypes() {}
}
