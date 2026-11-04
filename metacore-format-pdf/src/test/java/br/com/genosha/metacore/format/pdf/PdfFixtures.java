package br.com.genosha.metacore.format.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** Gera PDFs de teste por código, com o próprio PDFBox. */
public final class PdfFixtures {

  private PdfFixtures() {}

  /** Uma página, sem Info nem XMP. */
  public static byte[] simple() {
    return build(1, 1.7f, Map.of(), null, null);
  }

  /** Três páginas e dicionário Info completo, com datas com e sem fuso e uma chave própria. */
  public static byte[] withInfo() {
    return build(3, 1.7f, standardInfo(), null, null);
  }

  /** Data de criação sem fuso declarado. */
  public static byte[] dateWithoutOffset() {
    return build(1, 1.7f, Map.of("CreationDate", "D:20260714163205"), null, null);
  }

  /** Data que não segue o formato de PDF. */
  public static byte[] invalidDate() {
    return build(1, 1.7f, Map.of("CreationDate", "yesterday"), null, null);
  }

  /** PDF 1.7 com títulos diferentes no Info e no XMP: vale o Info. */
  public static byte[] infoAndXmp() {
    return build(1, 1.7f, Map.of("Title", "Info Title"), xmp("XMP Title"), null);
  }

  /** PDF 2.0 com títulos diferentes no Info e no XMP: vale o XMP. */
  public static byte[] pdf20InfoAndXmp() {
    return build(1, 2.0f, Map.of("Title", "Info Title"), xmp("XMP Title"), null);
  }

  /** XMP com DOCTYPE e entidade externa apontando para {@code secretUri}. */
  public static byte[] xmpWithExternalEntity(String secretUri) {
    String packet =
        "<?xml version=\"1.0\"?>\n<!DOCTYPE x:xmpmeta [<!ENTITY xxe SYSTEM \""
            + secretUri
            + "\">]>\n"
            + xmpBody(
                "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">&xxe;</rdf:li></rdf:Alt></dc:title>");
    return build(1, 1.7f, Map.of(), packet, null);
  }

  /** Protegido com senha de usuário: sem a senha, nada é legível. */
  public static byte[] userPassword() {
    return build(
        1,
        1.7f,
        standardInfo(),
        null,
        new StandardProtectionPolicy("owner", "secret", new AccessPermission()));
  }

  /** Criptografado só com senha de proprietário: abre sem senha. */
  public static byte[] ownerPasswordOnly() {
    return build(
        1,
        1.7f,
        Map.of("Title", "Protected"),
        null,
        new StandardProtectionPolicy("owner", "", new AccessPermission()));
  }

  public static byte[] truncate(byte[] content, int length) {
    return Arrays.copyOf(content, length);
  }

  private static Map<String, String> standardInfo() {
    return Map.of(
        "Title", "Relatório anual",
        "Author", "Genosha",
        "Subject", "Metadados",
        "Keywords", "pdf, metadata; testes",
        "Creator", "MetaCore tests",
        "Producer", "PDFBox",
        "CreationDate", "D:20260714163205-03'00'",
        "ModDate", "D:20260801090000Z",
        "Department", "Engenharia");
  }

  private static String xmp(String title) {
    return "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
        + xmpBody(
            "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">"
                + title
                + "</rdf:li></rdf:Alt></dc:title>"
                + "<dc:creator><rdf:Seq><rdf:li>Genosha</rdf:li></rdf:Seq></dc:creator>"
                + "<xmp:CreateDate>2026-07-14T16:32:05-03:00</xmp:CreateDate>"
                + "<pdf:Producer>PDFBox</pdf:Producer>")
        + "\n<?xpacket end=\"w\"?>";
  }

  private static String xmpBody(String properties) {
    return "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
        + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
        + "<rdf:Description rdf:about=\"\""
        + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
        + " xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\""
        + " xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">"
        + properties
        + "</rdf:Description></rdf:RDF></x:xmpmeta>";
  }

  /** Monta o PDF. As entradas do Info são gravadas como texto, exatamente como informadas. */
  private static byte[] build(
      int pages,
      float version,
      Map<String, String> info,
      String xmpPacket,
      StandardProtectionPolicy protection) {
    try (PDDocument document = new PDDocument()) {
      document.setVersion(version);
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      COSDictionary infoDictionary = document.getDocumentInformation().getCOSObject();
      info.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(e -> infoDictionary.setString(COSName.getPDFName(e.getKey()), e.getValue()));
      if (xmpPacket != null) {
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmpPacket.getBytes(StandardCharsets.UTF_8));
        document.getDocumentCatalog().setMetadata(metadata);
      }
      if (protection != null) {
        protection.setEncryptionKeyLength(128);
        document.protect(protection);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
