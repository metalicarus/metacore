package br.com.genosha.metacore.isolation.internal;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.FileInfo;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.BooleanValue;
import br.com.genosha.metacore.metadata.DecimalValue;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.LocalDateTimeValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import br.com.genosha.metacore.metadata.RationalValue;
import br.com.genosha.metacore.metadata.TextValue;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Protocolo entre quem chama e o worker (ADR 0016), sobre stdin e stdout do worker.
 *
 * <pre>
 * worker -> MAGIC, VERSION
 * pai    -> limites
 * worker -> READY | STARTUP_ERROR mensagem
 * pai    -> ANALYZE_PATH caminho | ANALYZE_STREAM nome?
 * worker -> READ máximo        (só para stream; o pai responde com um bloco, EOF ou SOURCE_ERROR)
 * worker -> RESULT resultado | ERROR tipo mensagem
 * </pre>
 *
 * A leitura confere tamanhos e quantidades antes de alocar e reconstrói tudo pelos construtores
 * públicos; qualquer inconsistência vira {@link ProtocolException}.
 */
public final class Protocol {

  public static final int MAGIC = 0x4D435752; // "MCWR"
  public static final int VERSION = 1;

  // Worker -> pai.
  public static final int READY = 1;
  public static final int STARTUP_ERROR = 2;
  public static final int RESULT = 3;
  public static final int ERROR = 4;
  public static final int READ = 5;

  // Pai -> worker.
  public static final int ANALYZE_PATH = 1;
  public static final int ANALYZE_STREAM = 2;

  // Resposta a READ: quantidade de bytes do bloco, ou um destes valores.
  public static final int EOF = -1;
  public static final int SOURCE_ERROR = -2;

  /** Maior bloco de stream pedido pelo worker. */
  public static final int CHUNK = 64 * 1024;

  // Tipos de ERROR.
  public static final int INVALID_INPUT = 1;
  public static final int LIMIT_EXCEEDED = 2;
  public static final int INTERNAL = 3;

  private static final int TEXT = 1;
  private static final int INTEGER = 2;
  private static final int DECIMAL = 3;
  private static final int RATIONAL = 4;
  private static final int BOOLEAN = 5;
  private static final int LOCAL_DATE_TIME = 6;
  private static final int OFFSET_DATE_TIME = 7;
  private static final int BINARY = 8;
  private static final int LIST = 9;

  /** Mensagens e textos fora dos limites da análise, como mensagens de aviso. */
  private static final int MAX_MESSAGE_BYTES = 1024 * 1024;

  private static final int MAX_ITEMS = 1 << 20;
  private static final int MAX_LIST_DEPTH = 32;
  private static final int MAX_DECIMAL_BYTES = 4096;

  private Protocol() {}

  // ---------------------------------------------------------------- escrita

  public static void writeString(DataOutput out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  public static void writeOptionalString(DataOutput out, Optional<String> value)
      throws IOException {
    out.writeBoolean(value.isPresent());
    if (value.isPresent()) {
      writeString(out, value.get());
    }
  }

  public static void writeLimits(DataOutput out, AnalysisLimits limits) throws IOException {
    out.writeLong(limits.maxFileSize());
    out.writeInt(limits.maxDetectionBytes());
    out.writeInt(limits.spoolMemoryThreshold());
    out.writeInt(limits.maxMetadataEntries());
    out.writeInt(limits.maxValueLength());
    out.writeInt(limits.maxBinaryValueBytes());
    out.writeLong(limits.maxDecompressedTextBytes());
    out.writeBoolean(limits.maxAnalysisTime().isPresent());
    if (limits.maxAnalysisTime().isPresent()) {
      out.writeLong(limits.maxAnalysisTime().get().toNanos());
    }
  }

  public static void writeResult(DataOutput out, MetadataResult result) throws IOException {
    FileInfo file = result.file();
    writeOptionalString(out, file.name());
    out.writeBoolean(file.size().isPresent());
    if (file.size().isPresent()) {
      out.writeLong(file.size().getAsLong());
    }
    writeOptionalString(out, file.extension());

    out.writeBoolean(result.detection().isPresent());
    if (result.detection().isPresent()) {
      writeDetection(out, result.detection().get());
    }
    writeString(out, result.status().name());

    out.writeInt(result.metadata().size());
    for (Metadata metadata : result.metadata()) {
      writeOptionalString(out, metadata.key());
      writeValue(out, metadata.value());
      writeString(out, metadata.rawName());
      writeString(out, metadata.namespace());
      writeString(out, metadata.source());
      out.writeBoolean(metadata.primary());
    }

    out.writeInt(result.warnings().size());
    for (Warning warning : result.warnings()) {
      writeString(out, warning.code().value());
      out.writeBoolean(warning.code().dataLoss());
      writeString(out, warning.message());
      writeOptionalString(out, warning.location());
    }
    out.writeLong(result.duration().toNanos());
  }

  private static void writeDetection(DataOutput out, DetectionResult detection) throws IOException {
    FileFormat format = detection.format();
    writeString(out, format.id());
    writeString(out, format.mimeType());
    writeString(out, format.displayName());
    out.writeInt(format.extensions().size());
    for (String extension : format.extensions()) {
      writeString(out, extension);
    }
    writeOptionalString(out, format.parent());
    writeString(out, detection.confidence().name());
    out.writeInt(detection.evidence().size());
    for (Evidence evidence : detection.evidence()) {
      writeString(out, evidence.name());
    }
    writeString(out, detection.detectorId());
  }

  private static void writeValue(DataOutput out, MetadataValue value) throws IOException {
    switch (value) {
      case TextValue v -> {
        out.writeByte(TEXT);
        writeString(out, v.value());
      }
      case IntegerValue v -> {
        out.writeByte(INTEGER);
        out.writeLong(v.value());
      }
      case DecimalValue v -> {
        out.writeByte(DECIMAL);
        byte[] unscaled = v.value().unscaledValue().toByteArray();
        out.writeInt(v.value().scale());
        out.writeInt(unscaled.length);
        out.write(unscaled);
      }
      case RationalValue v -> {
        out.writeByte(RATIONAL);
        out.writeLong(v.numerator());
        out.writeLong(v.denominator());
      }
      case BooleanValue v -> {
        out.writeByte(BOOLEAN);
        out.writeBoolean(v.value());
      }
      case LocalDateTimeValue v -> {
        out.writeByte(LOCAL_DATE_TIME);
        writeLocalDateTime(out, v.value());
      }
      case OffsetDateTimeValue v -> {
        out.writeByte(OFFSET_DATE_TIME);
        writeLocalDateTime(out, v.value().toLocalDateTime());
        out.writeInt(v.value().getOffset().getTotalSeconds());
      }
      case BinaryValue v -> {
        out.writeByte(BINARY);
        out.writeLong(v.size());
        Optional<byte[]> content = v.content();
        out.writeBoolean(content.isPresent());
        if (content.isPresent()) {
          out.writeInt(content.get().length);
          out.write(content.get());
        }
      }
      case ListValue v -> {
        out.writeByte(LIST);
        out.writeInt(v.values().size());
        for (MetadataValue element : v.values()) {
          writeValue(out, element);
        }
      }
    }
  }

  private static void writeLocalDateTime(DataOutput out, LocalDateTime value) throws IOException {
    out.writeLong(value.toEpochSecond(ZoneOffset.UTC));
    out.writeInt(value.getNano());
  }

  // ---------------------------------------------------------------- leitura

  public static String readMessage(DataInput in) throws IOException {
    return readString(in, MAX_MESSAGE_BYTES);
  }

  public static String readString(DataInput in, int maxBytes) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > maxBytes) {
      throw new ProtocolException("String of " + length + " bytes exceeds " + maxBytes);
    }
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public static AnalysisLimits readLimits(DataInput in) throws IOException {
    try {
      long maxFileSize = in.readLong();
      int maxDetectionBytes = in.readInt();
      int spoolMemoryThreshold = in.readInt();
      int maxMetadataEntries = in.readInt();
      int maxValueLength = in.readInt();
      int maxBinaryValueBytes = in.readInt();
      long maxDecompressedTextBytes = in.readLong();
      Optional<Duration> maxAnalysisTime =
          in.readBoolean() ? Optional.of(Duration.ofNanos(in.readLong())) : Optional.empty();
      return new AnalysisLimits(
          maxFileSize,
          maxDetectionBytes,
          spoolMemoryThreshold,
          maxMetadataEntries,
          maxValueLength,
          maxBinaryValueBytes,
          maxDecompressedTextBytes,
          maxAnalysisTime);
    } catch (IllegalArgumentException e) {
      throw new ProtocolException("Invalid limits: " + e.getMessage());
    }
  }

  /** Lê um resultado, recusando o que os limites da análise não permitiriam. */
  public static MetadataResult readResult(DataInput in, AnalysisLimits limits) throws IOException {
    try {
      return new ResultReader(in, limits).read();
    } catch (IllegalArgumentException | NullPointerException | DateTimeException e) {
      throw new ProtocolException("Invalid result: " + e.getMessage());
    }
  }

  private static final class ResultReader {

    private final DataInput in;
    private final AnalysisLimits limits;
    private final int maxTextBytes;

    ResultReader(DataInput in, AnalysisLimits limits) {
      this.in = in;
      this.limits = limits;
      // Até 3 bytes UTF-8 por char; um par substituto (2 chars) ocupa 4.
      long text = 3L * limits.maxValueLength();
      this.maxTextBytes = (int) Math.min(Integer.MAX_VALUE - 8, Math.max(text, MAX_MESSAGE_BYTES));
    }

    MetadataResult read() throws IOException {
      Optional<String> name = optionalString();
      OptionalLong size = in.readBoolean() ? OptionalLong.of(in.readLong()) : OptionalLong.empty();
      FileInfo file = new FileInfo(name, size, optionalString());

      Optional<DetectionResult> detection =
          in.readBoolean() ? Optional.of(detection()) : Optional.empty();
      ExtractionStatus status = ExtractionStatus.valueOf(string());

      int metadataCount = count(limits.maxMetadataEntries());
      List<Metadata> metadata = new ArrayList<>(Math.min(metadataCount, 1024));
      for (int i = 0; i < metadataCount; i++) {
        Optional<String> key = optionalString();
        MetadataValue value = value(0);
        metadata.add(new Metadata(key, value, string(), string(), string(), in.readBoolean()));
      }

      int warningCount = count(MAX_ITEMS);
      List<Warning> warnings = new ArrayList<>(Math.min(warningCount, 1024));
      for (int i = 0; i < warningCount; i++) {
        WarningCode code = new WarningCode(string(), in.readBoolean());
        warnings.add(new Warning(code, string(), optionalString()));
      }
      return new MetadataResult(
          file, detection, status, metadata, warnings, Duration.ofNanos(in.readLong()));
    }

    private DetectionResult detection() throws IOException {
      String id = string();
      String mimeType = string();
      String displayName = string();
      int extensionCount = count(1024);
      List<String> extensions = new ArrayList<>(extensionCount);
      for (int i = 0; i < extensionCount; i++) {
        extensions.add(string());
      }
      FileFormat format = new FileFormat(id, mimeType, displayName, extensions, optionalString());
      Confidence confidence = Confidence.valueOf(string());
      int evidenceCount = count(Evidence.values().length);
      Set<Evidence> evidence = EnumSet.noneOf(Evidence.class);
      for (int i = 0; i < evidenceCount; i++) {
        evidence.add(Evidence.valueOf(string()));
      }
      return new DetectionResult(format, confidence, evidence, string());
    }

    private MetadataValue value(int depth) throws IOException {
      int type = in.readUnsignedByte();
      return switch (type) {
        case TEXT -> new TextValue(string());
        case INTEGER -> new IntegerValue(in.readLong());
        case DECIMAL -> {
          int scale = in.readInt();
          int length = in.readInt();
          if (length <= 0 || length > MAX_DECIMAL_BYTES) {
            throw new ProtocolException("Invalid decimal of " + length + " bytes");
          }
          byte[] unscaled = new byte[length];
          in.readFully(unscaled);
          yield new DecimalValue(new BigDecimal(new BigInteger(unscaled), scale));
        }
        case RATIONAL -> new RationalValue(in.readLong(), in.readLong());
        case BOOLEAN -> new BooleanValue(in.readBoolean());
        case LOCAL_DATE_TIME -> new LocalDateTimeValue(localDateTime());
        case OFFSET_DATE_TIME -> {
          LocalDateTime local = localDateTime();
          yield new OffsetDateTimeValue(
              OffsetDateTime.of(local, ZoneOffset.ofTotalSeconds(in.readInt())));
        }
        case BINARY -> {
          long size = in.readLong();
          if (!in.readBoolean()) {
            yield BinaryValue.sizeOnly(size);
          }
          int length = count(limits.maxBinaryValueBytes());
          if (length != size) {
            throw new ProtocolException("Binary content does not match its size");
          }
          byte[] content = new byte[length];
          in.readFully(content);
          yield BinaryValue.of(content);
        }
        case LIST -> {
          if (depth >= MAX_LIST_DEPTH) {
            throw new ProtocolException("Lists nested deeper than " + MAX_LIST_DEPTH);
          }
          int length = count(MAX_ITEMS);
          List<MetadataValue> values = new ArrayList<>(Math.min(length, 1024));
          for (int i = 0; i < length; i++) {
            values.add(value(depth + 1));
          }
          yield new ListValue(values);
        }
        default -> throw new ProtocolException("Unknown value type " + type);
      };
    }

    private LocalDateTime localDateTime() throws IOException {
      long epochSecond = in.readLong();
      int nano = in.readInt();
      return LocalDateTime.ofEpochSecond(epochSecond, nano, ZoneOffset.UTC);
    }

    private int count(int max) throws IOException {
      int count = in.readInt();
      if (count < 0 || count > max) {
        throw new ProtocolException("Count " + count + " outside 0.." + max);
      }
      return count;
    }

    private String string() throws IOException {
      return readString(in, maxTextBytes);
    }

    private Optional<String> optionalString() throws IOException {
      return in.readBoolean() ? Optional.of(string()) : Optional.empty();
    }
  }
}
