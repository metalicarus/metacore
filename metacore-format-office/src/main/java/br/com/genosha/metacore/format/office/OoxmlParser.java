package br.com.genosha.metacore.format.office;

import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_AUTHOR;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_CREATED;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_KEYWORDS;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_LAST_MODIFIED_BY;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_MODIFIED;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_PAGE_COUNT;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_PRODUCER;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_SUBJECT;
import static br.com.genosha.metacore.metadata.MetadataKeys.DOCUMENT_TITLE;

import br.com.genosha.metacore.format.office.internal.Inflation;
import br.com.genosha.metacore.format.office.internal.LocalScan;
import br.com.genosha.metacore.format.office.internal.MainContentTypes;
import br.com.genosha.metacore.format.office.internal.W3cDates;
import br.com.genosha.metacore.format.office.internal.XmlPart;
import br.com.genosha.metacore.format.office.internal.ZipArchive;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.ParseContext;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import javax.xml.stream.XMLStreamException;

/**
 * Extrai metadados de ZIP e de pacotes Office Open XML com um leitor próprio (ADR 0018). Do ZIP,
 * vêm o número de entradas, o tamanho declarado e o comentário. De um pacote OOXML, vêm o tipo da
 * parte principal, a presença de macros e as propriedades {@code core}, {@code app} e {@code
 * custom}. O conteúdo do documento nunca é lido.
 *
 * <p>Cada parte XML é descompactada até {@code maxDecompressedTextBytes}, o que fecha a porta para
 * zip bombs, e lida sem DTD, o que fecha a porta para XXE. Sem o diretório central, como num
 * arquivo truncado, as partes são procuradas pelos cabeçalhos locais.
 */
public final class OoxmlParser implements MetadataParser {

  static final String ZIP_NAMESPACE = "ZIP";
  static final String PACKAGE_NAMESPACE = "OOXML";
  static final String CORE_NAMESPACE = "OOXML:Core";
  static final String APP_NAMESPACE = "OOXML:App";
  static final String CUSTOM_NAMESPACE = "OOXML:Custom";

  private static final String DC = "http://purl.org/dc/elements/1.1/";
  private static final String DCTERMS = "http://purl.org/dc/terms/";
  private static final String CP =
      "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";
  private static final Map<String, String> PREFIXES =
      Map.of(DC, "dc", DCTERMS, "dcterms", CP, "cp");

  private static final String CONTENT_TYPES = "[Content_Types].xml";
  private static final String RELATIONSHIPS = "_rels/.rels";
  private static final String CORE = "docProps/core.xml";
  private static final String APP = "docProps/app.xml";
  private static final String CUSTOM = "docProps/custom.xml";

  /** Teto do que é descompactado só para achar o fim de uma entrada, sem o diretório central. */
  private static final long MAX_SCANNED = 64L * 1024 * 1024;

  private static final Pattern INTEGER = Pattern.compile("-?\\d{1,18}");
  private static final Pattern KEYWORD_SEPARATOR = Pattern.compile("[,;]");

  private record CoreMapping(String key, boolean date, boolean list) {}

  private static final Map<String, CoreMapping> CORE_KEYS =
      Map.of(
          "dc:title", new CoreMapping(DOCUMENT_TITLE, false, false),
          "dc:creator", new CoreMapping(DOCUMENT_AUTHOR, false, false),
          "dc:subject", new CoreMapping(DOCUMENT_SUBJECT, false, false),
          "cp:keywords", new CoreMapping(DOCUMENT_KEYWORDS, false, true),
          "cp:lastModifiedBy", new CoreMapping(DOCUMENT_LAST_MODIFIED_BY, false, false),
          "dcterms:created", new CoreMapping(DOCUMENT_CREATED, true, false),
          "dcterms:modified", new CoreMapping(DOCUMENT_MODIFIED, true, false));

  private static final Set<String> CORE_DATES = Set.of("cp:lastPrinted");

  @Override
  public String id() {
    return "ooxml";
  }

  @Override
  public Set<String> supportedFormats() {
    return Set.of(
        OfficeFormats.ZIP.id(),
        OfficeFormats.DOCX.id(),
        OfficeFormats.XLSX.id(),
        OfficeFormats.PPTX.id());
  }

  /** Acesso às partes do pacote, pelo diretório central ou pelos cabeçalhos locais. */
  private interface Parts {
    /** Conteúdo da parte, até o limite; vazio se ela não existe. */
    Optional<Inflation> read(String name) throws IOException;

    /** Indica se alguma entrada termina com o sufixo (sem diferenciar maiúsculas). */
    boolean hasEntryEndingWith(String suffix);
  }

  @Override
  public void parse(InputSource source, ParseContext context) throws IOException {
    int maxPart =
        (int) Math.min(context.limits().maxDecompressedTextBytes(), Integer.MAX_VALUE - 8);
    try (SeekableByteChannel channel = source.openChannel()) {
      ZipArchive archive;
      try {
        archive = ZipArchive.read(channel);
      } catch (ZipArchive.NotAnArchiveException e) {
        context.warn(
            WarningCode.TRUNCATED,
            e.getMessage() + "; the file may be truncated, so entries were read one by one",
            "central directory");
        parsePackage(scan(source, maxPart, context), context);
        return;
      }
      emitArchive(archive, context);
      parsePackage(central(archive, maxPart), context);
    }
  }

  private static void emitArchive(ZipArchive archive, ParseContext context) {
    List<ZipArchive.Entry> entries = archive.entries();
    context.emit(MetadataEntry.raw(ZIP_NAMESPACE, "Entries", MetadataValue.of(entries.size())));
    long size = entries.stream().mapToLong(ZipArchive.Entry::size).sum();
    context.emit(MetadataEntry.raw(ZIP_NAMESPACE, "Uncompressed Size", MetadataValue.of(size)));
    if (entries.stream().anyMatch(ZipArchive.Entry::encrypted)) {
      context.emit(MetadataEntry.raw(ZIP_NAMESPACE, "Encrypted Entries", MetadataValue.of(true)));
    }
    if (!archive.comment().isEmpty()) {
      context.emit(
          MetadataEntry.raw(ZIP_NAMESPACE, "Comment", MetadataValue.of(archive.comment())));
    }
  }

  private static Parts central(ZipArchive archive, int maxPart) {
    return new Parts() {
      @Override
      public Optional<Inflation> read(String name) throws IOException {
        Optional<ZipArchive.Entry> entry = archive.find(name);
        return entry.isEmpty() ? Optional.empty() : Optional.of(archive.read(entry.get(), maxPart));
      }

      @Override
      public boolean hasEntryEndingWith(String suffix) {
        String wanted = suffix.toLowerCase(Locale.ROOT);
        return archive.entries().stream()
            .anyMatch(e -> e.name().toLowerCase(Locale.ROOT).endsWith(wanted));
      }
    };
  }

  /** Partes achadas percorrendo os cabeçalhos locais; só as de metadados são guardadas. */
  private static Parts scan(InputSource source, int maxPart, ParseContext context)
      throws IOException {
    Map<String, Inflation> found = new HashMap<>();
    List<String> names = new ArrayList<>();
    try (InputStream in = source.openStream()) {
      boolean complete =
          LocalScan.scan(
              in,
              OoxmlParser::metadataPart,
              maxPart,
              MAX_SCANNED,
              (name, content) -> {
                context.checkCancelled();
                names.add(name.toLowerCase(Locale.ROOT));
                if (content != null) {
                  found.putIfAbsent(name.toLowerCase(Locale.ROOT), content);
                }
                return true;
              });
      if (!complete) {
        context.warn(
            WarningCode.TRUNCATED,
            "Entries after the first " + names.size() + " could not be read",
            "local headers");
      }
    }
    return new Parts() {
      @Override
      public Optional<Inflation> read(String name) {
        return Optional.ofNullable(found.get(name.toLowerCase(Locale.ROOT)));
      }

      @Override
      public boolean hasEntryEndingWith(String suffix) {
        String wanted = suffix.toLowerCase(Locale.ROOT);
        return names.stream().anyMatch(n -> n.endsWith(wanted));
      }
    };
  }

  private static boolean metadataPart(String entry) {
    String name = entry.toLowerCase(Locale.ROOT);
    return name.equals("[content_types].xml")
        || name.equals(RELATIONSHIPS)
        || (name.startsWith("docprops/") && name.endsWith(".xml"));
  }

  private static void parsePackage(Parts parts, ParseContext context) throws IOException {
    Optional<XmlPart> contentTypes = xml(parts, CONTENT_TYPES, context);
    if (contentTypes.isEmpty()) {
      if (!context.detection().format().id().equals(OfficeFormats.ZIP.id())) {
        context.warn(
            WarningCode.CORRUPTED_SEGMENT,
            "The package has no readable " + CONTENT_TYPES,
            CONTENT_TYPES);
      }
      return;
    }
    Optional<String> mainType =
        contentTypes.get().children().stream()
            .map(c -> c.attributes().getOrDefault("ContentType", ""))
            .filter(t -> MainContentTypes.formatId(t).isPresent())
            .findFirst();
    mainType.ifPresent(
        t ->
            context.emit(
                MetadataEntry.raw(PACKAGE_NAMESPACE, "Content Type", MetadataValue.of(t))));
    boolean macros =
        mainType.map(MainContentTypes::macroEnabled).orElse(false)
            || parts.hasEntryEndingWith("vbaProject.bin");
    context.emit(MetadataEntry.raw(PACKAGE_NAMESPACE, "Macros", MetadataValue.of(macros)));

    Map<String, String> targets = relationships(parts, context);
    context.checkCancelled();
    xml(parts, targets.getOrDefault("core-properties", CORE), context)
        .ifPresent(core -> core(core, context));
    context.checkCancelled();
    xml(parts, targets.getOrDefault("extended-properties", APP), context)
        .ifPresent(app -> app(app, context));
    context.checkCancelled();
    xml(parts, targets.getOrDefault("custom-properties", CUSTOM), context)
        .ifPresent(custom -> custom(custom, context));
  }

  /** Alvos das relações do pacote, pelo final do tipo, como {@code core-properties}. */
  private static Map<String, String> relationships(Parts parts, ParseContext context)
      throws IOException {
    Map<String, String> targets = new HashMap<>();
    Optional<XmlPart> rels = xml(parts, RELATIONSHIPS, context);
    for (XmlPart relationship : rels.map(XmlPart::children).orElse(List.of())) {
      String type = relationship.attributes().getOrDefault("Type", "");
      String target = relationship.attributes().getOrDefault("Target", "");
      if (target.isEmpty() || "External".equals(relationship.attributes().get("TargetMode"))) {
        continue;
      }
      String kind = type.substring(type.lastIndexOf('/') + 1);
      targets.putIfAbsent(kind, target.startsWith("/") ? target.substring(1) : target);
    }
    return targets;
  }

  private static Optional<XmlPart> xml(Parts parts, String name, ParseContext context)
      throws IOException {
    Optional<Inflation> content;
    try {
      content = parts.read(name);
    } catch (ZipException e) {
      context.warn(
          WarningCode.CORRUPTED_SEGMENT, "Part could not be read: " + e.getMessage(), name);
      return Optional.empty();
    }
    if (content.isEmpty()) {
      return Optional.empty();
    }
    if (content.get().overflow()) {
      context.warn(
          WarningCode.LIMIT_REACHED,
          "Part is larger than maxDecompressedTextBytes ("
              + context.limits().maxDecompressedTextBytes()
              + " bytes) and was skipped",
          name);
      return Optional.empty();
    }
    if (!content.get().finished()) {
      context.warn(WarningCode.TRUNCATED, "Part is truncated", name);
      return Optional.empty();
    }
    try {
      return Optional.of(XmlPart.parse(content.get().data()));
    } catch (XMLStreamException e) {
      context.warn(WarningCode.CORRUPTED_SEGMENT, "Part is not valid XML: " + firstLine(e), name);
      return Optional.empty();
    }
  }

  private static void core(XmlPart core, ParseContext context) {
    for (XmlPart property : core.children()) {
      String prefix = PREFIXES.get(property.namespace());
      String rawName = prefix == null ? property.name() : prefix + ":" + property.name();
      String text = String.join(" ", property.leafTexts()).strip();
      if (text.isEmpty()) {
        continue;
      }
      CoreMapping mapping = CORE_KEYS.get(rawName);
      if (mapping == null) {
        MetadataValue value =
            CORE_DATES.contains(rawName)
                ? W3cDates.parse(text).orElse(MetadataValue.of(text))
                : MetadataValue.of(text);
        context.emit(MetadataEntry.raw(CORE_NAMESPACE, rawName, value));
      } else if (mapping.list()) {
        List<MetadataValue> items =
            Arrays.stream(KEYWORD_SEPARATOR.split(text))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .<MetadataValue>map(MetadataValue::of)
                .toList();
        if (!items.isEmpty()) {
          context.emit(
              MetadataEntry.normalized(
                  mapping.key(), new ListValue(items), CORE_NAMESPACE, rawName));
        }
      } else if (mapping.date()) {
        Optional<MetadataValue> date = W3cDates.parse(text);
        if (date.isPresent()) {
          context.emit(
              MetadataEntry.normalized(mapping.key(), date.get(), CORE_NAMESPACE, rawName));
        } else {
          context.warn(
              WarningCode.VALUE_CONVERSION_FAILED,
              "'" + text + "' is not a W3CDTF date",
              CORE_NAMESPACE + " " + rawName);
          context.emit(MetadataEntry.raw(CORE_NAMESPACE, rawName, MetadataValue.of(text)));
        }
      } else {
        context.emit(
            MetadataEntry.normalized(
                mapping.key(), MetadataValue.of(text), CORE_NAMESPACE, rawName));
      }
    }
  }

  /** {@code docProps/app.xml}: valores simples ou vetores, como os nomes das planilhas. */
  private static void app(XmlPart app, ParseContext context) {
    for (XmlPart property : app.children()) {
      MetadataValue value;
      if (property.children().isEmpty()) {
        if (property.text().isEmpty()) {
          continue;
        }
        value = simple(property.text());
      } else {
        List<MetadataValue> items =
            property.leafTexts().stream().<MetadataValue>map(MetadataValue::of).toList();
        if (items.isEmpty()) {
          continue;
        }
        value = new ListValue(items);
      }
      String name = property.name();
      Optional<String> key =
          switch (name) {
            case "Application" -> Optional.of(DOCUMENT_PRODUCER);
            // Apresentações contam slides, não páginas.
            case "Pages", "Slides" ->
                value instanceof IntegerValue ? Optional.of(DOCUMENT_PAGE_COUNT) : Optional.empty();
            default -> Optional.empty();
          };
      if (key.isPresent()) {
        if (key.get().equals(DOCUMENT_PRODUCER)) {
          value = MetadataValue.of(property.text());
        }
        context.emit(MetadataEntry.normalized(key.get(), value, APP_NAMESPACE, name));
      } else {
        context.emit(MetadataEntry.raw(APP_NAMESPACE, name, value));
      }
    }
  }

  /** {@code docProps/custom.xml}: propriedades definidas pelo usuário, com tipos do VT. */
  private static void custom(XmlPart custom, ParseContext context) {
    for (XmlPart property : custom.children()) {
      String name = property.attributes().getOrDefault("name", "").strip();
      if (name.isEmpty() || property.children().isEmpty()) {
        continue;
      }
      XmlPart typed = property.children().get(0);
      String text = typed.text();
      context.emit(MetadataEntry.raw(CUSTOM_NAMESPACE, name, typed(typed.name(), text)));
    }
  }

  private static MetadataValue typed(String type, String text) {
    try {
      return switch (type) {
        case "i1", "i2", "i4", "i8", "int", "ui1", "ui2", "ui4", "ui8", "uint" ->
            INTEGER.matcher(text).matches()
                ? MetadataValue.of(Long.parseLong(text))
                : MetadataValue.of(new BigDecimal(text));
        case "r4", "r8", "decimal", "cy" -> MetadataValue.of(new BigDecimal(text));
        case "bool" -> MetadataValue.of("true".equalsIgnoreCase(text) || "1".equals(text));
        case "filetime", "date" -> W3cDates.parse(text).orElse(MetadataValue.of(text));
        default -> MetadataValue.of(text);
      };
    } catch (NumberFormatException e) {
      return MetadataValue.of(text);
    }
  }

  private static MetadataValue simple(String text) {
    if (INTEGER.matcher(text).matches()) {
      return MetadataValue.of(Long.parseLong(text));
    }
    if (text.equals("true") || text.equals("false")) {
      return MetadataValue.of(Boolean.parseBoolean(text));
    }
    return MetadataValue.of(text);
  }

  private static String firstLine(Exception e) {
    String message = String.valueOf(e.getMessage());
    int newline = message.indexOf('\n');
    return (newline < 0 ? message : message.substring(newline + 1)).strip();
  }
}
