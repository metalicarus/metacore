package br.com.genosha.metacore.format.office;

import br.com.genosha.metacore.format.FileFormat;
import java.util.List;
import java.util.Optional;

/** Formatos declarados por este módulo. Os formatos OOXML descendem de {@code zip}. */
public final class OfficeFormats {

  public static final FileFormat ZIP = FileFormat.of("zip", "application/zip", "ZIP", "zip");

  public static final FileFormat DOCX =
      ooxml(
          "docx",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "DOCX",
          "docx",
          "docm",
          "dotx",
          "dotm");

  public static final FileFormat XLSX =
      ooxml(
          "xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "XLSX",
          "xlsx",
          "xlsm",
          "xltx",
          "xltm");

  public static final FileFormat PPTX =
      ooxml(
          "pptx",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "PPTX",
          "pptx",
          "pptm",
          "potx",
          "potm",
          "ppsx",
          "ppsm");

  private OfficeFormats() {}

  private static FileFormat ooxml(
      String id, String mimeType, String displayName, String... extensions) {
    return new FileFormat(id, mimeType, displayName, List.of(extensions), Optional.of(ZIP.id()));
  }
}
