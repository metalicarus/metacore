package br.com.genosha.metacore.format.pdf;

import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_AUTHOR;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_CREATED;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_CREATOR;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_FORMAT_VERSION;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_KEYWORDS;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_MODIFIED;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_PAGE_COUNT;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_PRODUCER;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_SUBJECT;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_TITLE;

import br.com.genosha.metacore.format.pdf.internal.ChannelRandomAccessRead;
import br.com.genosha.metacore.format.pdf.internal.PdfDates;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.ParseContext;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.options.IteratorOptions;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/**
 * Extrai metadados de PDF com o PDFBox: versão, dicionário Info, XMP do catálogo e número de
 * páginas. O conteúdo das páginas nunca é lido.
 *
 * <p>Precedência entre Info e XMP (docs/keys.md): até a versão 1.7 vale o Info; no PDF 2.0, que
 * deprecia o Info, vale o XMP.
 */
public final class PdfParser implements MetadataParser {

  static final String PDF_NAMESPACE = "PDF";
  static final String INFO_NAMESPACE = "PDF:Info";
  static final String XMP_NAMESPACE = "XMP";

  private static final int EOF_SEARCH = 1024;
  private static final byte[] EOF_MARKER = "%%EOF".getBytes(StandardCharsets.US_ASCII);

  private record Mapping(String key, Function<String, Optional<MetadataValue>> converter) {}

  private static final Function<String, Optional<MetadataValue>> TEXT =
      value -> value.isBlank() ? Optional.empty() : Optional.of(MetadataValue.of(value.trim()));

  private static final Function<String, Optional<MetadataValue>> KEYWORDS =
      value -> {
        List<MetadataValue> items =
            Arrays.stream(value.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .<MetadataValue>map(MetadataValue::of)
                .toList();
        return items.isEmpty() ? Optional.empty() : Optional.of(new ListValue(items));
      };

  private static final Map<String, Mapping> INFO =
      Map.of(
          "Title", new Mapping(DOCUMENT_TITLE, TEXT),
          "Author", new Mapping(DOCUMENT_AUTHOR, TEXT),
          "Subject", new Mapping(DOCUMENT_SUBJECT, TEXT),
          "Keywords", new Mapping(DOCUMENT_KEYWORDS, KEYWORDS),
          "Creator", new Mapping(DOCUMENT_CREATOR, TEXT),
          "Producer", new Mapping(DOCUMENT_PRODUCER, TEXT),
          "CreationDate", new Mapping(DOCUMENT_CREATED, PdfDates::parsePdf),
          "ModDate", new Mapping(DOCUMENT_MODIFIED, PdfDates::parsePdf));

  private static final Map<String, Mapping> XMP =
      Map.of(
          "dc:title[1]", new Mapping(DOCUMENT_TITLE, TEXT),
          "dc:creator[1]", new Mapping(DOCUMENT_AUTHOR, TEXT),
          "dc:description[1]", new Mapping(DOCUMENT_SUBJECT, TEXT),
          "pdf:Keywords", new Mapping(DOCUMENT_KEYWORDS, KEYWORDS),
          "xmp:CreatorTool", new Mapping(DOCUMENT_CREATOR, TEXT),
          "pdf:Producer", new Mapping(DOCUMENT_PRODUCER, TEXT),
          "xmp:CreateDate", new Mapping(DOCUMENT_CREATED, PdfDates::parseXmp),
          "xmp:ModifyDate", new Mapping(DOCUMENT_MODIFIED, PdfDates::parseXmp));

  @Override
  public String id() {
    return "pdf";
  }

  @Override
  public Set<String> supportedFormats() {
    return Set.of(PdfFormats.PDF.id());
  }

  @Override
  public void parse(InputSource source, ParseContext context) throws IOException {
    if (!hasEofMarker(source)) {
      context.warn(
          WarningCode.TRUNCATED,
          "No %%EOF marker in the last " + EOF_SEARCH + " bytes; the file may be truncated",
          "end of file");
    }
    try (SeekableByteChannel channel = source.openChannel();
        PDDocument document =
            Loader.loadPDF(
                new ChannelRandomAccessRead(channel, source.size()),
                "",
                IOUtils.createMemoryOnlyStreamCache())) {
      context.checkCancelled();
      emit(document, context);
    } catch (InvalidPasswordException e) {
      context.warn(
          WarningCode.ENCRYPTED, "Document requires a password; its metadata cannot be read");
      context.emit(MetadataEntry.raw(PDF_NAMESPACE, "Encrypted", MetadataValue.of(true)));
    }
  }

  private static void emit(PDDocument document, ParseContext context) {
    float version = document.getVersion();
    boolean pdf2 = version >= 2.0f;
    int infoPrecedence = pdf2 ? 2 : 1;
    int xmpPrecedence = pdf2 ? 1 : 2;

    context.emit(
        new MetadataEntry(
            Optional.of(DOCUMENT_FORMAT_VERSION),
            MetadataValue.of(Float.toString(version)),
            "Version",
            PDF_NAMESPACE,
            1));
    if (document.isEncrypted()) {
      context.emit(MetadataEntry.raw(PDF_NAMESPACE, "Encrypted", MetadataValue.of(true)));
    }
    try {
      context.emit(
          new MetadataEntry(
              Optional.of(DOCUMENT_PAGE_COUNT),
              MetadataValue.of(document.getNumberOfPages()),
              "Pages/Count",
              PDF_NAMESPACE,
              1));
    } catch (RuntimeException e) {
      context.warn(
          WarningCode.CORRUPTED_SEGMENT,
          "Could not read the page tree: " + e.getMessage(),
          "Pages");
    }

    emitInfo(document.getDocumentInformation().getCOSObject(), infoPrecedence, context);
    context.checkCancelled();
    emitXmp(document, xmpPrecedence, context);
  }

  private static void emitInfo(COSDictionary info, int precedence, ParseContext context) {
    for (Map.Entry<COSName, COSBase> entry : info.entrySet()) {
      String name = entry.getKey().getName();
      COSBase value =
          entry.getValue() instanceof COSObject ref ? ref.getObject() : entry.getValue();
      Optional<MetadataValue> raw = rawValue(value);
      if (raw.isEmpty()) {
        continue;
      }
      Mapping mapping = INFO.get(name);
      if (mapping != null && value instanceof COSString text) {
        Optional<MetadataValue> converted = mapping.converter().apply(text.getString());
        if (converted.isPresent()) {
          context.emit(
              new MetadataEntry(
                  Optional.of(mapping.key()), converted.get(), name, INFO_NAMESPACE, precedence));
          continue;
        }
        conversionFailed(context, INFO_NAMESPACE, name, mapping.key());
      }
      context.emit(MetadataEntry.raw(INFO_NAMESPACE, name, raw.get()));
    }
  }

  private static void emitXmp(PDDocument document, int precedence, ParseContext context) {
    PDMetadata metadata = document.getDocumentCatalog().getMetadata();
    if (metadata == null) {
      return;
    }
    long limit = context.limits().maxDecompressedTextBytes();
    byte[] packet;
    try (InputStream in = metadata.createInputStream()) {
      packet = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
    } catch (IOException e) {
      context.warn(
          WarningCode.CORRUPTED_SEGMENT,
          "Could not read the XMP stream: " + e.getMessage(),
          XMP_NAMESPACE);
      return;
    }
    if (packet.length > limit) {
      context.warn(
          WarningCode.LIMIT_REACHED,
          "XMP stream exceeds maxDecompressedTextBytes of " + limit + " bytes; skipped",
          XMP_NAMESPACE);
      return;
    }
    Map<String, String> properties = new TreeMap<>();
    try {
      XMPMeta xmp = XMPMetaFactory.parseFromBuffer(packet);
      XMPIterator iterator =
          xmp.iterator(new IteratorOptions().setJustLeafnodes(true).setOmitQualifiers(true));
      while (iterator.hasNext()) {
        XMPPropertyInfo property = (XMPPropertyInfo) iterator.next();
        if (property.getPath() != null && property.getValue() != null) {
          properties.put(property.getPath(), property.getValue());
        }
      }
    } catch (XMPException e) {
      context.warn(
          WarningCode.CORRUPTED_SEGMENT, "Invalid XMP metadata: " + e.getMessage(), XMP_NAMESPACE);
      return;
    }
    for (Map.Entry<String, String> property : properties.entrySet()) {
      String path = property.getKey();
      Mapping mapping = XMP.get(path);
      if (mapping != null) {
        Optional<MetadataValue> converted = mapping.converter().apply(property.getValue());
        if (converted.isPresent()) {
          context.emit(
              new MetadataEntry(
                  Optional.of(mapping.key()), converted.get(), path, XMP_NAMESPACE, precedence));
          continue;
        }
        conversionFailed(context, XMP_NAMESPACE, path, mapping.key());
      }
      context.emit(MetadataEntry.raw(XMP_NAMESPACE, path, MetadataValue.of(property.getValue())));
    }
  }

  private static Optional<MetadataValue> rawValue(COSBase value) {
    return switch (value) {
      case null -> Optional.empty();
      case COSNull n -> Optional.empty();
      case COSString s -> Optional.of(MetadataValue.of(s.getString()));
      case COSName n -> Optional.of(MetadataValue.of(n.getName()));
      case COSInteger i -> Optional.of(MetadataValue.of(i.longValue()));
      case COSFloat f ->
          Optional.of(MetadataValue.of(new BigDecimal(Float.toString(f.floatValue()))));
      case COSBoolean b -> Optional.of(MetadataValue.of(b.getValue()));
      default -> Optional.of(MetadataValue.of(value.toString()));
    };
  }

  private static boolean hasEofMarker(InputSource source) throws IOException {
    long size = source.size();
    int tail = (int) Math.min(size, EOF_SEARCH);
    ByteBuffer buffer = ByteBuffer.allocate(tail);
    try (SeekableByteChannel channel = source.openChannel()) {
      channel.position(size - tail);
      while (buffer.hasRemaining() && channel.read(buffer) != -1) {
        // lê o final do arquivo
      }
    }
    byte[] bytes = Arrays.copyOf(buffer.array(), buffer.position());
    for (int i = bytes.length - EOF_MARKER.length; i >= 0; i--) {
      if (Arrays.equals(bytes, i, i + EOF_MARKER.length, EOF_MARKER, 0, EOF_MARKER.length)) {
        return true;
      }
    }
    return false;
  }

  private static void conversionFailed(
      ParseContext context, String namespace, String rawName, String key) {
    context.warn(
        WarningCode.VALUE_CONVERSION_FAILED,
        "Could not convert '" + rawName + "' to '" + key + "'; kept as a raw field",
        namespace + "/" + rawName);
  }
}
