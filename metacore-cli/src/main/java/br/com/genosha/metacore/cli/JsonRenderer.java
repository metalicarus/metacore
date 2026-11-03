package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.FileInfo;
import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.MetaCoreException;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.metadata.BinaryValue;
import br.com.genosha.metacore.metadata.BooleanValue;
import br.com.genosha.metacore.metadata.DecimalValue;
import br.com.genosha.metacore.metadata.IntegerValue;
import br.com.genosha.metacore.metadata.ListValue;
import br.com.genosha.metacore.metadata.LocalDateTimeValue;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.MetadataKeys;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.OffsetDateTimeValue;
import br.com.genosha.metacore.metadata.RationalValue;
import br.com.genosha.metacore.metadata.TextValue;
import br.com.genosha.metacore.metadata.Warning;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Contrato JSON da CLI (blueprint, seção 8.4; ADR 0011). Cada entrada vira um objeto numa linha
 * (NDJSON). A ordem dos campos é fixa e a duração da análise não é incluída.
 */
final class JsonRenderer {

  /** Versão do schema JSON. Muda só com alterações incompatíveis. */
  static final String SCHEMA_VERSION = "1";

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build();

  private final boolean includeBinary;

  JsonRenderer(boolean includeBinary) {
    this.includeBinary = includeBinary;
  }

  String inspect(MetadataResult result, Optional<String> path) {
    ObjectNode root = NODES.objectNode();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("keysVersion", MetadataKeys.VERSION);
    root.set("file", file(result.file(), path));
    root.set("detection", detection(result.detection()));
    root.put("status", result.status().name());
    ArrayNode metadata = root.putArray("metadata");
    result.metadata().forEach(m -> metadata.add(metadata(m)));
    root.set("warnings", warnings(result.warnings()));
    return write(root);
  }

  String identify(IdentificationResult result, Optional<String> path) {
    ObjectNode root = NODES.objectNode();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.set("file", file(result.file(), path));
    root.set("detection", detection(result.detection()));
    root.set("warnings", warnings(result.warnings()));
    return write(root);
  }

  /** Entrada que não pôde ser analisada. {@code input} é o caminho como foi informado. */
  String error(String input, MetaCoreException failure) {
    ObjectNode root = NODES.objectNode();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("input", input);
    ObjectNode error = root.putObject("error");
    error.put(
        "type",
        failure instanceof InputLimitExceededException ? "INPUT_LIMIT_EXCEEDED" : "INVALID_INPUT");
    error.put("message", failure.getMessage());
    return write(root);
  }

  private static ObjectNode file(FileInfo file, Optional<String> path) {
    ObjectNode node = NODES.objectNode();
    node.put("name", file.name().orElse(null));
    node.put("size", file.size());
    node.put("extension", file.extension().orElse(null));
    path.ifPresent(p -> node.put("path", p));
    return node;
  }

  private static JsonNode detection(Optional<DetectionResult> detection) {
    if (detection.isEmpty()) {
      return NODES.nullNode();
    }
    DetectionResult result = detection.get();
    FileFormat format = result.format();
    ObjectNode node = NODES.objectNode();
    ObjectNode formatNode = node.putObject("format");
    formatNode.put("id", format.id());
    formatNode.put("mimeType", format.mimeType());
    formatNode.put("displayName", format.displayName());
    formatNode.put("parent", format.parent().orElse(null));
    node.put("confidence", result.confidence().name());
    ArrayNode evidence = node.putArray("evidence");
    result.evidence().forEach(e -> evidence.add(e.name()));
    node.put("detectorId", result.detectorId());
    return node;
  }

  private ObjectNode metadata(Metadata metadata) {
    ObjectNode node = NODES.objectNode();
    node.put("key", metadata.key().orElse(null));
    node.set("value", value(metadata.value()));
    node.put("valueType", metadata.value().type().name());
    node.put("rawName", metadata.rawName());
    node.put("namespace", metadata.namespace());
    node.put("source", metadata.source());
    node.put("primary", metadata.primary());
    return node;
  }

  private JsonNode value(MetadataValue value) {
    return switch (value) {
      case TextValue t -> NODES.textNode(t.value());
      case IntegerValue i -> NODES.numberNode(i.value());
      case DecimalValue d -> NODES.numberNode(d.value());
      case RationalValue r -> {
        ObjectNode node = NODES.objectNode();
        node.put("numerator", r.numerator());
        node.put("denominator", r.denominator());
        yield node;
      }
      case BooleanValue b -> NODES.booleanNode(b.value());
      case LocalDateTimeValue l ->
          NODES.textNode(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(l.value()));
      case OffsetDateTimeValue o ->
          NODES.textNode(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(o.value()));
      case BinaryValue b -> {
        ObjectNode node = NODES.objectNode();
        node.put("size", b.size());
        if (includeBinary) {
          b.content().ifPresent(c -> node.put("base64", Base64.getEncoder().encodeToString(c)));
        }
        yield node;
      }
      case ListValue l -> {
        ArrayNode array = NODES.arrayNode();
        for (MetadataValue item : l.values()) {
          ObjectNode entry = array.addObject();
          entry.set("value", value(item));
          entry.put("valueType", item.type().name());
        }
        yield array;
      }
    };
  }

  private static ArrayNode warnings(List<Warning> warnings) {
    ArrayNode array = NODES.arrayNode();
    for (Warning warning : warnings) {
      ObjectNode node = array.addObject();
      node.put("code", warning.code().value());
      node.put("message", warning.message());
      node.put("location", warning.location().orElse(null));
    }
    return array;
  }

  private static String write(ObjectNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
