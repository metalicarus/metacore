package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.MetaCoreException;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.metadata.Metadata;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * CSV (RFC 4180) com uma linha por entrada. As colunas fixas vêm primeiro; no {@code inspect}, cada
 * chave normalizada encontrada vira uma coluna, em ordem alfabética. Por isso as linhas são
 * guardadas e escritas no fim.
 *
 * <p>Textos que começam com {@code = + - @}, tabulação ou CR ganham um apóstrofo na frente, para
 * que planilhas não os executem como fórmulas: os valores vêm de arquivos possivelmente hostis.
 */
final class CsvReport implements Report {

  private static final List<String> INSPECT_COLUMNS =
      List.of("file", "format", "mime_type", "status", "warnings", "error");
  private static final List<String> IDENTIFY_COLUMNS =
      List.of("file", "format", "mime_type", "confidence", "warnings", "error");

  private final PrintWriter out;
  private final KeyFilter keys;
  private final boolean showPath;
  private final List<Map<String, String>> rows = new ArrayList<>();
  private final TreeSet<String> metadataColumns = new TreeSet<>();

  CsvReport(PrintWriter out, KeyFilter keys, boolean showPath) {
    this.out = out;
    this.keys = keys;
    this.showPath = showPath;
  }

  @Override
  public void inspected(Input input, MetadataResult result) {
    Map<String, String> row = row(input, result.detection());
    row.put("status", result.status().name());
    row.put("warnings", Integer.toString(result.warnings().size()));
    for (Metadata m : result.metadata()) {
      if (m.primary() && keys.selects(m)) {
        String key = m.key().orElseThrow();
        metadataColumns.add(key);
        row.put(key, Values.plain(m.value()));
      }
    }
    rows.add(row);
  }

  @Override
  public void identified(Input input, IdentificationResult result) {
    Map<String, String> row = row(input, result.detection());
    row.put(
        "confidence",
        result.detection().map(d -> d.confidence().name().toLowerCase(Locale.ROOT)).orElse(""));
    row.put("warnings", Integer.toString(result.warnings().size()));
    rows.add(row);
  }

  @Override
  public void failed(Input input, MetaCoreException failure) {
    Map<String, String> row = row(input, Optional.empty());
    row.put("status", "ERROR");
    row.put("error", failure.getMessage());
    rows.add(row);
  }

  @Override
  public void finish(Tally tally) {
    List<String> columns = new ArrayList<>(tally.identify() ? IDENTIFY_COLUMNS : INSPECT_COLUMNS);
    if (!tally.identify()) {
      columns.addAll(metadataColumns);
    }
    line(columns);
    for (Map<String, String> row : rows) {
      line(columns.stream().map(c -> row.getOrDefault(c, "")).toList());
    }
  }

  private Map<String, String> row(Input input, Optional<DetectionResult> detection) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("file", showPath ? input.given() : input.label());
    detection.ifPresent(
        d -> {
          row.put("format", d.format().id());
          row.put("mime_type", d.format().mimeType());
        });
    return row;
  }

  private void line(List<String> values) {
    StringBuilder line = new StringBuilder();
    for (String value : values) {
      if (!line.isEmpty()) {
        line.append(',');
      }
      line.append(field(value));
    }
    out.print(line.append("\r\n"));
  }

  static String field(String value) {
    String text = value;
    if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !numeric(text)) {
      text = "'" + text;
    }
    if (text.chars().anyMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r')) {
      return '"' + text.replace("\"", "\"\"") + '"';
    }
    return text;
  }

  private static boolean numeric(String text) {
    return text.matches("[-+]?\\d+(\\.\\d+)?(/\\d+)?");
  }
}
