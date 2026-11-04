package br.com.genosha.metacore.cli;

import br.com.genosha.metacore.api.IdentificationResult;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.MetaCoreException;
import java.io.PrintWriter;
import java.util.Optional;

/**
 * NDJSON: um objeto por entrada, inclusive as que falharam, que viram uma linha com {@code error}.
 */
final class JsonReport implements Report {

  private final PrintWriter out;
  private final JsonRenderer json;
  private final KeyFilter keys;
  private final boolean showPath;

  JsonReport(PrintWriter out, JsonRenderer json, KeyFilter keys, boolean showPath) {
    this.out = out;
    this.json = json;
    this.keys = keys;
    this.showPath = showPath;
  }

  @Override
  public void inspected(Input input, MetadataResult result) {
    MetadataResult selected =
        keys.all()
            ? result
            : new MetadataResult(
                result.file(),
                result.detection(),
                result.status(),
                result.metadata().stream().filter(keys::selects).toList(),
                result.warnings(),
                result.duration());
    out.println(json.inspect(selected, path(input)));
  }

  @Override
  public void identified(Input input, IdentificationResult result) {
    out.println(json.identify(result, path(input)));
  }

  @Override
  public void failed(Input input, MetaCoreException failure) {
    out.println(json.error(input.given(), failure));
  }

  private Optional<String> path(Input input) {
    return showPath ? input.path().map(Object::toString) : Optional.empty();
  }
}
