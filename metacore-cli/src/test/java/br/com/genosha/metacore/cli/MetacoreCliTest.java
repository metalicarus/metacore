package br.com.genosha.metacore.cli;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.format.office.OfficeFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        .contains("photo.jpg", "JPEG", "Camera", "Make", "Genosha", "1/125 s")
        .contains("more fields (--verbose)")
        .doesNotContain(dir.toString())
        .doesNotContain("\u001b[");
    assertThat(run.err()).isEmpty();
  }

  @Test
  void verboseShowsRawFieldsWithTheirOrigin() {
    CliRun run = CliRun.run("inspect", "--verbose", photo.toString());

    assertThat(run.out())
        .contains("Raw fields", "EXIF:SubIFD", "Exif Version", "<binary, 4 bytes>")
        .contains("-> image.camera.make");
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
    assertThat(textRun.out()).contains(photo + "  JPEG");
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

    assertThat(run.out()).contains("notes.txt", "unknown format");
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
    assertThat(CliRun.run("inspect").exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "-v", "-q", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "--max-file-size", "lots", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "-o", "xml", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
    assertThat(CliRun.run("inspect", "-j", "0", photo.toString()).exitCode())
        .isEqualTo(ExitCodes.USAGE);
  }

  @Test
  void usageErrorsAreShortAndSuggestFixes() {
    CliRun run = CliRun.run("inspect", "--jobz", "2", photo.toString());

    assertThat(run.exitCode()).isEqualTo(ExitCodes.USAGE);
    assertThat(run.err())
        .contains("Unknown option: '--jobz'", "--jobs", "metacore inspect --help")
        .doesNotContain("Usage:");
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
  void withoutArgumentsShowsTheBannerAndExamples() {
    CliRun run = CliRun.run();

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains(Banner.LINES[1], "metacore photo.jpg", "metacore --help");
    assertThat(CliRun.run("--version").out()).contains(Banner.LINES[1], "metacore ");
  }

  @Test
  void inspectIsTheDefaultCommand() {
    assertThat(CliRun.run(photo.toString()).out())
        .isEqualTo(CliRun.run("inspect", photo.toString()).out());
    assertThat(CliRun.run("-o", "json", photo.toString()).out())
        .isEqualTo(CliRun.run("inspect", "-o", "json", photo.toString()).out());
  }

  @Test
  void summarizesSeveralInputs() {
    CliRun run = CliRun.run(photo.toString(), truncated.toString(), text.toString());

    assertThat(run.out()).contains("3 files", "1 ok", "1 partial", "1 unknown");
    assertThat(CliRun.run(photo.toString()).out()).doesNotContain("1 files");
  }

  @Test
  void analyzesDirectories() throws IOException {
    Path photos = Files.createDirectories(dir.resolve("photos"));
    Files.copy(photo, photos.resolve("a.jpg"));
    Files.copy(text, photos.resolve("b.TXT"));
    Path trip = Files.createDirectories(photos.resolve("trip"));
    Files.copy(photo, trip.resolve("c.JPG"));
    Path hidden = Files.createDirectories(photos.resolve(".cache"));
    Files.copy(photo, hidden.resolve("d.jpg"));

    CliRun flat = CliRun.run("-q", photos.toString());
    assertThat(flat.outLines()).containsExactly("a.jpg: SUCCESS (JPEG)", "b.TXT: UNKNOWN_FORMAT");
    assertThat(flat.err()).contains("skipped 1 subdirectory", "-r");

    CliRun recursive = CliRun.run("-q", "-r", photos.toString());
    assertThat(recursive.outLines())
        .containsExactly(
            "a.jpg: SUCCESS (JPEG)",
            "b.TXT: UNKNOWN_FORMAT",
            "trip" + dir.getFileSystem().getSeparator() + "c.JPG: SUCCESS (JPEG)");
    assertThat(recursive.err()).isEmpty();

    assertThat(CliRun.run("-q", "-r", "--include", "*.jpg", photos.toString()).outLines())
        .hasSize(2)
        .allMatch(l -> l.contains("JPEG"));
    assertThat(CliRun.run("-q", "-r", "--exclude", "*.jpg", photos.toString()).outLines())
        .containsExactly("b.TXT: UNKNOWN_FORMAT");
    assertThat(CliRun.run("-q", "-r", "--hidden", photos.toString()).outLines()).hasSize(4);
    assertThat(CliRun.run("-q", Files.createDirectories(dir.resolve("empty")).toString()).err())
        .contains("no files to analyze");
  }

  @Test
  void parallelJobsKeepTheOrderOfTheInputs() throws IOException {
    Path many = Files.createDirectories(dir.resolve("many"));
    for (int i = 0; i < 20; i++) {
      Files.copy(i % 2 == 0 ? photo : text, many.resolve(String.format("f%02d", i)));
    }

    CliRun sequential = CliRun.run("-o", "json", many.toString());
    CliRun parallel = CliRun.run("-o", "json", "-j", "4", many.toString());

    assertThat(parallel.out()).isEqualTo(sequential.out());
    assertThat(parallel.outLines()).hasSize(20);
  }

  @Test
  void readsStandardInput() throws IOException {
    InputStream original = System.in;
    try {
      System.setIn(new ByteArrayInputStream(Files.readAllBytes(photo)));
      CliRun run = CliRun.run("-o", "json", "-");
      System.setIn(new ByteArrayInputStream(Files.readAllBytes(photo)));
      CliRun identify = CliRun.run("identify", "-");

      JsonNode root = JSON.readTree(run.out());
      assertThat(root.path("status").asText()).isEqualTo("SUCCESS");
      assertThat(root.path("file").path("name").isNull()).isTrue();
      assertThat(identify.out()).contains("<stdin>", "JPEG");
      assertThat(CliRun.run("-", "-").exitCode()).isEqualTo(ExitCodes.FAILURE);
    } finally {
      System.setIn(original);
    }
  }

  @Test
  void selectsFieldsByKey() throws IOException {
    CliRun text = CliRun.run("-k", "*.make,*.MODEL", "-k", "image.datetime_*", photo.toString());
    assertThat(text.out())
        .contains("Make", "Model", "Datetime original")
        .doesNotContain("Width", "more fields");

    JsonNode root =
        JSON.readTree(CliRun.run("-o", "json", "-k", "*.width", photo.toString()).out());
    assertThat(root.path("metadata"))
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.path("key").asText()).isEqualTo("image.width"));
  }

  @Test
  void writesCsvWithOneRowPerInput() {
    CliRun run =
        CliRun.run(
            "-o",
            "csv",
            "-k",
            "image.width,image.camera.make",
            photo.toString(),
            text.toString(),
            dir.resolve("missing.jpg").toString());

    assertThat(run.exitCode()).isEqualTo(ExitCodes.FAILURE);
    assertThat(run.outLines())
        .containsExactly(
            "file,format,mime_type,status,warnings,error,image.camera.make,image.width",
            "photo.jpg,jpeg,image/jpeg,SUCCESS,0,,Genosha,64",
            "notes.txt,,,UNKNOWN_FORMAT,0,,,",
            "missing.jpg,,,ERROR,,File not found: " + dir.resolve("missing.jpg") + ",,");
    assertThat(CliRun.run("identify", "-o", "csv", photo.toString()).outLines())
        .containsExactly(
            "file,format,mime_type,confidence,warnings,error",
            "photo.jpg,jpeg,image/jpeg,exact,0,");
  }

  @Test
  void colorsOnlyWhenAsked() {
    assertThat(CliRun.run(photo.toString()).out()).doesNotContain("\u001b[");
    assertThat(CliRun.run("--color", "always", photo.toString()).out()).contains("\u001b[32m");
    assertThat(CliRun.run("--color", "never", photo.toString()).out()).doesNotContain("\u001b[");
  }

  @Test
  void inspectsOfficeDocuments() throws IOException {
    Path docx = Files.write(dir.resolve("report.docx"), OfficeFixtures.docx());

    CliRun run = CliRun.run(docx.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .contains(
            "report.docx", "DOCX", "Document", "Relatório anual", "Last modified by", "Revisor");
    assertThat(CliRun.run("-o", "csv", "-k", "document.page_count", docx.toString()).outLines())
        .containsExactly(
            "file,format,mime_type,status,warnings,error,document.page_count",
            "report.docx,docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document,SUCCESS,0,,3");
  }

  @Test
  void helpGroupsTheOptions() {
    assertThat(CliRun.run("inspect", "--help").out())
        .contains("Input:", "Output:", "Fields:", "Analysis:", "Isolation:");
    assertThat(CliRun.run("--help").out()).contains(Banner.LINES[1], "completion");
    assertThat(CliRun.run("completion").out()).contains("metacore");
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
