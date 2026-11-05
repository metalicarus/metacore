package br.com.genosha.metacore.format.office;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.office.internal.LocalScan;
import br.com.genosha.metacore.format.office.internal.MainContentTypes;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Reconhece ZIP pela assinatura e, dentro dele, DOCX, XLSX e PPTX. O Office, o LibreOffice e as
 * bibliotecas comuns gravam {@code [Content_Types].xml} como primeira entrada: quando ela está no
 * início lido, o content type da parte principal decide o formato com confiança {@code EXACT}. Sem
 * ela, a confiança é {@code HIGH} quando aparece a parte principal ({@code word/document.xml}) ou
 * suas relações, ou uma entrada como {@code word/} junto de uma parte comum a todo pacote OPC.
 */
public final class OoxmlDetector implements FormatDetector {

  static final String CONTENT_TYPES = "[Content_Types].xml";
  private static final int MAX_CONTENT_TYPES = 64 * 1024;
  private static final int MAX_ENTRIES = 64;

  @Override
  public String id() {
    return "zip-ooxml";
  }

  @Override
  public Optional<DetectionCandidate> detect(DetectionRequest request) {
    boolean entry = request.startsWith('P', 'K', 3, 4);
    if (!entry && !request.startsWith('P', 'K', 5, 6)) {
      return Optional.empty();
    }
    if (!entry) {
      return Optional.of(
          DetectionCandidate.of(OfficeFormats.ZIP.id(), Confidence.HIGH, Evidence.MAGIC));
    }
    Findings findings = new Findings();
    byte[] header = new byte[request.length()];
    request.header().get(header);
    try {
      LocalScan.scan(
          new ByteArrayInputStream(header),
          CONTENT_TYPES::equalsIgnoreCase,
          MAX_CONTENT_TYPES,
          MAX_CONTENT_TYPES,
          (name, content) -> {
            findings.entries++;
            if (content != null) {
              findings.mainType =
                  MainContentTypes.findMain(new String(content.data(), StandardCharsets.UTF_8));
            }
            findings.byName = findings.byName.or(() -> byName(name));
            findings.byMainPart = findings.byMainPart.or(() -> byMainPart(name));
            findings.opc |= opcPart(name);
            return findings.entries < MAX_ENTRIES && findings.mainType.isEmpty();
          });
    } catch (IOException | RuntimeException e) {
      // o início não é um ZIP bem formado; vale o que já foi visto
    }
    EnumSet<Evidence> structure = EnumSet.of(Evidence.MAGIC, Evidence.STRUCTURE);
    Optional<String> fromType = findings.mainType.flatMap(MainContentTypes::formatId);
    if (fromType.isPresent()) {
      return Optional.of(new DetectionCandidate(fromType.get(), Confidence.EXACT, structure));
    }
    Optional<String> fromNames =
        findings.byMainPart.or(() -> findings.opc ? findings.byName : Optional.empty());
    if (fromNames.isPresent()) {
      return Optional.of(new DetectionCandidate(fromNames.get(), Confidence.HIGH, structure));
    }
    return Optional.of(
        findings.entries > 0
            ? new DetectionCandidate(OfficeFormats.ZIP.id(), Confidence.EXACT, structure)
            : DetectionCandidate.of(OfficeFormats.ZIP.id(), Confidence.HIGH, Evidence.MAGIC));
  }

  private static Optional<String> byName(String entry) {
    String name = entry.toLowerCase(Locale.ROOT);
    if (name.startsWith("word/")) {
      return Optional.of(OfficeFormats.DOCX.id());
    }
    if (name.startsWith("xl/")) {
      return Optional.of(OfficeFormats.XLSX.id());
    }
    if (name.startsWith("ppt/")) {
      return Optional.of(OfficeFormats.PPTX.id());
    }
    return Optional.empty();
  }

  /**
   * A parte principal de cada tipo, ou as relações dela. O LibreOffice grava essas entradas antes
   * de {@code [Content_Types].xml} e de {@code _rels/.rels}.
   */
  private static Optional<String> byMainPart(String entry) {
    return switch (entry.toLowerCase(Locale.ROOT)) {
      case "word/document.xml", "word/_rels/document.xml.rels" ->
          Optional.of(OfficeFormats.DOCX.id());
      case "xl/workbook.xml", "xl/_rels/workbook.xml.rels" -> Optional.of(OfficeFormats.XLSX.id());
      case "ppt/presentation.xml", "ppt/_rels/presentation.xml.rels" ->
          Optional.of(OfficeFormats.PPTX.id());
      default -> Optional.empty();
    };
  }

  /** Partes que todo pacote OPC tem e que um ZIP qualquer dificilmente teria. */
  private static boolean opcPart(String entry) {
    String name = entry.toLowerCase(Locale.ROOT);
    return name.equals("[content_types].xml")
        || name.equals("_rels/.rels")
        || name.startsWith("docprops/");
  }

  private static final class Findings {
    int entries;
    boolean opc;
    Optional<String> mainType = Optional.empty();
    Optional<String> byName = Optional.empty();
    Optional<String> byMainPart = Optional.empty();
  }
}
