package br.com.genosha.metacore.cli;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetacoreCliTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path dir;

  private Path photo;
  private Path truncated;
  private Path text;

  @BeforeEach
  void createInputs() throws IOException {
    photo = Files.write(dir.resolve("photo.jpg"), JpegFixtures.basicExif());
    byte[] full = JpegFixtures.basicExif();
    truncated = Files.write(dir.resolve("cut.jpg"), JpegFixtures.truncate(full, full.length / 2));
    text = Files.writeString(dir.resolve("notes.txt"), "just text");
  }

  @Test
  void inspectPrintsNormalizedFieldsByDefault() {
    CliRun run = CliRun.run("inspect", photo.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .contains("photo.jpg", "Status:   SUCCESS", "image.camera.make", "Genosha", "1/125")
        .contains("more fields; use --verbose")
        .doesNotContain(dir.toString());
    assertThat(run.err()).isEmpty();
  }

  @Test
  void verboseShowsRawFieldsWithTheirOrigin() {
    CliRun run = CliRun.run("inspect", "--verbose", photo.toString());

    assertThat(run.out()).contains("(EXIF:SubIFD / Exif Version)", "<binary, 4 bytes>");
  }

  @Test
  void quietPrintsOneLinePerInput() {
    CliRun run = CliRun.run("inspect", "-q", photo.toString(), text.toString());

    assertThat(run.outLines())
        .containsExactly("photo.jpg: SUCCESS (JPEG)", "notes.txt: UNKNOWN_FORMAT");
  }

  @Test
  void inspectJsonFollowsTheContract() throws IOException {
    CliRun run = CliRun.run("inspect", "--output", "json", photo.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.outLines()).hasSize(1);
    JsonNode root = JSON.readTree(run.out());
    assertThat(fieldNames(root))
        .containsExactly(
            "schemaVersion", "keysVersion", "file", "detection", "status", "metadata", "warnings");
    assertThat(root.path("file").has("path")).isFalse();
    assertThat(root.path("detection").path("format").path("id").asText()).isEqualTo("jpeg");

    JsonNode exposure = field(root, "image.camera.exposure_time");
    assertThat(exposure.path("valueType").asText()).isEqualTo("RATIONAL");
    assertThat(exposure.path("value").path("numerator").asLong()).isEqualTo(1);
    assertThat(exposure.path("value").path("denominator").asLong()).isEqualTo(125);
    assertThat(field(root, "image.datetime_original").path("value").asText())
        .isEqualTo("2026-07-14T16:32:05-03:00");

    JsonNode exifVersion = raw(root, "Exif Version");
    assertThat(exifVersion.path("key").isNull()).isTrue();
    assertThat(exifVersion.path("value").path("size").asInt()).isEqualTo(4);
    assertThat(exifVersion.path("value").has("base64")).isFalse();
  }

  @Test
  void includeBinaryAddsBase64Content() throws IOException {
    CliRun run = CliRun.run("inspect", "-o", "json", "--include-binary", photo.toString());

    assertThat(raw(JSON.readTree(run.out()), "Exif Version").path("value").path("base64").asText())
        .isEqualTo("MDIzMg==");
  }

  @Test
  void showPathIncludesThePathAsGiven() throws IOException {
    CliRun json = CliRun.run("inspect", "-o", "json", "--show-path", photo.toString());
    CliRun textRun = CliRun.run("identify", "--show-path", photo.toString());

    assertThat(JSON.readTree(json.out()).path("file").path("path").asText())
        .isEqualTo(photo.toString());
    assertThat(textRun.out()).startsWith(photo + ": JPEG");
  }

  @Test
  void multipleInputsProduceOneJsonLineEach() throws IOException {
    CliRun run =
        CliRun.run(
            "inspect",
            "-o",
            "json",
            photo.toString(),
            dir.resolve("missing.jpg").toString(),
            text.toString());

    assertThat(run.exitCode()).isEqualTo(ExitCodes.FAILURE);
    List<String> lines = run.outLines();
    assertThat(lines).hasSize(3);
    assertThat(JSON.readTree(lines.get(0)).path("status").asText()).isEqualTo("SUCCESS");
    JsonNode error = JSON.readTree(lines.get(1));
    assertThat(error.path("error").path("type").asText()).isEqualTo("INVALID_INPUT");
    assertThat(error.path("input").asText()).endsWith("missing.jpg");
    assertThat(JSON.readTree(lines.get(2)).path("status").asText()).isEqualTo("UNKNOWN_FORMAT");
    assertThat(run.err()).isEmpty();
  }

  @Test
  void textErrorsGoToStandardError() {
    CliRun run = CliRun.run("inspect", dir.resolve("missing.jpg").toString());

    assertThat(run.exitCode()).isEqualTo(ExitCodes.FAILURE);
    assertThat(run.out()).isEmpty();
    assertThat(run.err()).contains("missing.jpg", "File not found");
  }

  @Test
  void unknownAndPartialResultsAreNotFailuresUnlessStrict() {
    assertThat(CliRun.run("inspect", text.toString(), truncated.toString()).exitCode()).isZero();
    assertThat(CliRun.run("inspect", "--strict", text.toString()).exitCode())
        .isEqualTo(ExitCodes.STRICT);
    assertThat(CliRun.run("inspect", "--strict", truncated.toString()).exitCode())
        .isEqualTo(ExitCodes.STRICT);
    assertThat(CliRun.run("inspect", "--strict", photo.toString()).exitCode()).isZero();
  }

  @Test
  void failureOutranksStrictMode() {
    CliRun run =
        CliRun.run("inspect", "--strict", text.toString(), dir.resolve("missing.jpg").toString());

    assertThat(run.exitCode()).isEqualTo(ExitCodes.FAILURE);
  }

  @Test
  void identifyDoesNotExtractMetadata() throws IOException {
    CliRun run = CliRun.run("identify", "-o", "json", photo.toString());

    JsonNode root = JSON.readTree(run.out());
    assertThat(fieldNames(root)).containsExactly("schemaVersion", "file", "detection", "warnings");
    assertThat(root.path("detection").path("evidence"))
        .extracting(JsonNode::asText)
        .containsExactly("MAGIC", "STRUCTURE", "EXTENSION");
  }

  @Test
  void identifyReportsUnknownFormats() {
    CliRun run = CliRun.run("identify", text.toString());

    assertThat(run.out()).isEqualToIgnoringNewLines("notes.txt: unknown format");
    assertThat(run.exitCode()).isZero();
    assertThat(CliRun.run("identify", "--strict", text.toString()).exitCode())
        .isEqualTo(ExitCodes.STRICT);
  }

  @Test
  void writesToTheOutputFileInsteadOfStandardOutput() throws IOException {
    Path out = dir.resolve("result.json");

    CliRun run = CliRun.run("inspect", "-o", "json", "--file", out.toString(), photo.toString());

    assertThat(run.out()).isEmpty();
    assertThat(JSON.readTree(Files.readString(out, StandardCharsets.UTF_8)).path("status").asText())
        .isEqualTo("SUCCESS");
  }

  @Test
  void rejectsInputsAboveMaxFileSize() throws IOException {
    CliRun run = CliRun.run("inspect", "-o", "json", "--max-file-size", "1K", photo.toString());

    assertThat(run.exitCode()).isEqualTo(ExitCodes.FAILURE);
    assertThat(JSON.readTree(run.out()).path("error").path("type").asText())
        .isEqualTo("INPUT_LIMIT_EXCEEDED");
  }

  @Test
  void invalidUsageExitsWithTwo() {
    assertThat(CliRun.run().exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect").exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "-v", "-q", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "--max-file-size", "lots", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "-o", "xml", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
  }

  @Test
  void isolatedInspectGivesTheSameOutput() {
    String[] files = {photo.toString(), truncated.toString(), text.toString()};
    CliRun inProcess = CliRun.run(concat(new String[] {"inspect", "-o", "json"}, files));
    CliRun isolated =
        CliRun.run(concat(new String[] {"inspect", "-o", "json", "--timeout", "20s"}, files));

    assertThat(isolated.exitCode()).isEqualTo(inProcess.exitCode());
    assertThat(isolated.out()).isEqualTo(inProcess.out());
    assertThat(isolated.err()).isEmpty();
    assertThat(CliRun.run("inspect", "--timeout", "0s", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
  }

  private static String[] concat(String[] a, String[] b) {
    return java.util.stream.Stream.concat(java.util.Arrays.stream(a), java.util.Arrays.stream(b))
        .toArray(String[]::new);
  }

  @Test
  void helpMentionsPersonalDataAndExitCodes() {
    CliRun run = CliRun.run("--help");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("personal data", "Exit codes:", "identify", "inspect");
  }

  private static List<String> fieldNames(JsonNode node) {
    return StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
        .toList();
  }

  private static JsonNode field(JsonNode root, String key) {
    for (JsonNode m : root.path("metadata")) {
      if (m.path("primary").asBoolean() && key.equals(m.path("key").asText())) {
        return m;
      }
    }
    throw new AssertionError("missing " + key);
  }

  private static JsonNode raw(JsonNode root, String rawName) {
    for (JsonNode m : root.path("metadata")) {
      if (rawName.equals(m.path("rawName").asText())) {
        return m;
      }
    }
    throw new AssertionError("missing raw field " + rawName);
  }
}
