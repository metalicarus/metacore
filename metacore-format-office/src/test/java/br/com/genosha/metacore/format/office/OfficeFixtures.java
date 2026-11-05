package br.com.genosha.metacore.format.office;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Pacotes OOXML de teste. Os gerados aqui seguem a ordem do Microsoft Office ({@code
 * [Content_Types].xml} primeiro, sem descritores); os de {@code libreoffice/} foram gravados pelo
 * LibreOffice 24.2 a partir de documentos com propriedades preenchidas.
 */
public final class OfficeFixtures {

  static final String DOCX_MAIN =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
  static final String DOCM_MAIN = "application/vnd.ms-word.document.macroEnabled.main+xml";
  static final String XLSX_MAIN =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
  static final String PPTX_MAIN =
      "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";

  private OfficeFixtures() {}

  public static byte[] docx() {
    return office("word/document.xml", DOCX_MAIN, CORE, WORD_APP, CUSTOM).build();
  }

  /** DOCM: content type com macros e o projeto VBA. */
  public static byte[] docm() {
    return office("word/document.xml", DOCM_MAIN, CORE, WORD_APP, null)
        .add("word/vbaProject.bin", new byte[] {1, 2, 3})
        .build();
  }

  public static byte[] xlsx() {
    return office("xl/workbook.xml", XLSX_MAIN, CORE, EXCEL_APP, null).build();
  }

  public static byte[] pptx() {
    return office("ppt/presentation.xml", PPTX_MAIN, CORE, POWERPOINT_APP, null).build();
  }

  /** DOCX sem propriedades: só o necessário para abrir. */
  public static byte[] minimalDocx() {
    return office("word/document.xml", DOCX_MAIN, null, null, null).build();
  }

  /**
   * Um DOCX como o LibreOffice grava: descritores de dados e {@code [Content_Types].xml} no fim.
   */
  public static byte[] docxWithDescriptors() {
    return new ZipBuilder()
        .descriptors()
        .add("_rels/.rels", rootRelationships(true, true, true))
        .add("docProps/core.xml", CORE)
        .add("docProps/app.xml", WORD_APP)
        .add("docProps/custom.xml", CUSTOM)
        .add("word/document.xml", DOCUMENT)
        .add("[Content_Types].xml", contentTypes("word/document.xml", DOCX_MAIN))
        .build();
  }

  public static byte[] plainZip() {
    return new ZipBuilder()
        .add("notes/readme.txt", "hello")
        .stored("data.bin", new byte[] {0, 1, 2, 3})
        .comment("Backup de julho")
        .build();
  }

  /** Um arquivo cortado no meio, sem o diretório central. */
  public static byte[] truncated(byte[] archive, int length) {
    return Arrays.copyOf(archive, length);
  }

  public static byte[] libreOffice(String name) {
    try (InputStream in = OfficeFixtures.class.getResourceAsStream("libreoffice/" + name)) {
      if (in == null) {
        throw new IllegalArgumentException("No fixture " + name);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static ZipBuilder office(
      String mainPart, String mainType, String core, String app, String custom) {
    ZipBuilder zip =
        new ZipBuilder()
            .add("[Content_Types].xml", contentTypes(mainPart, mainType))
            .add("_rels/.rels", rootRelationships(core != null, app != null, custom != null));
    if (core != null) {
      zip.add("docProps/core.xml", core);
    }
    if (app != null) {
      zip.add("docProps/app.xml", app);
    }
    if (custom != null) {
      zip.add("docProps/custom.xml", custom);
    }
    return zip.add(mainPart, DOCUMENT);
  }

  static String contentTypes(String mainPart, String mainType) {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
        <Default Extension="xml" ContentType="application/xml"/>\
        <Override PartName="/%s" ContentType="%s"/>\
        <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>\
        <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>\
        </Types>"""
        .formatted(mainPart, mainType);
  }

  static String rootRelationships(boolean core, boolean app, boolean custom) {
    StringBuilder xml =
        new StringBuilder(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""");
    if (core) {
      xml.append(
          "<Relationship Id=\"rId2\""
              + " Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\""
              + " Target=\"docProps/core.xml\"/>");
    }
    if (app) {
      xml.append(
          "<Relationship Id=\"rId3\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\""
              + " Target=\"/docProps/app.xml\"/>");
    }
    if (custom) {
      xml.append(
          "<Relationship Id=\"rId4\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties\""
              + " Target=\"docProps/custom.xml\"/>");
    }
    return xml.append("</Relationships>").toString();
  }

  static String core(String body) {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" \
        xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" \
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">%s</cp:coreProperties>"""
        .formatted(body);
  }

  static final String CORE =
      core(
          """
          <dc:title>Relatório anual</dc:title>\
          <dc:subject>Metadados</dc:subject>\
          <dc:creator>Genosha</dc:creator>\
          <cp:keywords>office; metadados, testes</cp:keywords>\
          <dc:description>Arquivo de teste</dc:description>\
          <cp:lastModifiedBy>Revisor</cp:lastModifiedBy>\
          <cp:revision>7</cp:revision>\
          <cp:lastPrinted>2026-07-20T10:00:00Z</cp:lastPrinted>\
          <dcterms:created xsi:type="dcterms:W3CDTF">2026-07-14T16:32:05Z</dcterms:created>\
          <dcterms:modified xsi:type="dcterms:W3CDTF">2026-08-01T09:00:00-03:00</dcterms:modified>\
          <cp:category>Relatórios</cp:category>""");

  private static final String APP_HEADER =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Properties"
          + " xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\""
          + " xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">";

  static final String WORD_APP =
      APP_HEADER
          + "<Template>Normal.dotm</Template><TotalTime>12</TotalTime><Pages>3</Pages>"
          + "<Words>250</Words><Characters>1400</Characters>"
          + "<Application>Microsoft Office Word</Application><DocSecurity>0</DocSecurity>"
          + "<ScaleCrop>false</ScaleCrop><Company>Genosha</Company><AppVersion>16.0000</AppVersion>"
          + "</Properties>";

  static final String EXCEL_APP =
      APP_HEADER
          + "<Application>Microsoft Excel</Application>"
          + "<HeadingPairs><vt:vector size=\"2\" baseType=\"variant\">"
          + "<vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>"
          + "<vt:variant><vt:i4>2</vt:i4></vt:variant></vt:vector></HeadingPairs>"
          + "<TitlesOfParts><vt:vector size=\"2\" baseType=\"lpstr\">"
          + "<vt:lpstr>Vendas</vt:lpstr><vt:lpstr>Custos</vt:lpstr></vt:vector></TitlesOfParts>"
          + "<AppVersion>16.0300</AppVersion></Properties>";

  static final String POWERPOINT_APP =
      APP_HEADER
          + "<Application>Microsoft Office PowerPoint</Application><Slides>12</Slides>"
          + "<Notes>2</Notes><HiddenSlides>1</HiddenSlides></Properties>";

  static final String CUSTOM =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Properties"
          + " xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/custom-properties\""
          + " xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">"
          + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"2\" name=\"Projeto\">"
          + "<vt:lpwstr>MetaCore</vt:lpwstr></property>"
          + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"3\" name=\"Revisão\">"
          + "<vt:i4>42</vt:i4></property>"
          + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"4\" name=\"Custo\">"
          + "<vt:r8>1234.5</vt:r8></property>"
          + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"5\" name=\"Aprovado\">"
          + "<vt:bool>true</vt:bool></property>"
          + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"6\" name=\"Prazo\">"
          + "<vt:filetime>2026-12-31T23:59:00Z</vt:filetime></property>"
          + "</Properties>";

  static final String DOCUMENT =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document"
          + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
          + "<w:p><w:r><w:t>Conteúdo que nunca é lido</w:t></w:r></w:p></w:body></w:document>";
}
