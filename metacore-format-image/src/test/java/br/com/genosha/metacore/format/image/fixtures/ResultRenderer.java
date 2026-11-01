package br.com.genosha.metacore.format.image.fixtures;

import br.com.genosha.metacore.api.MetadataResult;
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
import java.util.stream.Collectors;

/** Representação textual estável de um resultado, usada nos golden files. */
public final class ResultRenderer {

  private ResultRenderer() {}

  public static String render(MetadataResult result) {
    StringBuilder out = new StringBuilder();
    out.append("status: ").append(result.status()).append('\n');
    out.append("format: ").append(result.format().map(f -> f.id()).orElse("-")).append('\n');
    out.append("metadata:\n");
    for (Metadata m : result.metadata()) {
      out.append("  ")
          .append(m.primary() ? "* " : "  ")
          .append(m.key().orElse("-"))
          .append(" | ")
          .append(m.namespace())
          .append(" | ")
          .append(m.rawName())
          .append(" | ")
          .append(m.value().type())
          .append(" | ")
          .append(value(m.value()))
          .append('\n');
    }
    out.append("warnings:\n");
    for (Warning w : result.warnings()) {
      out.append("  ")
          .append(w.code())
          .append(" | ")
          .append(w.location().orElse("-"))
          .append(" | ")
          .append(w.message())
          .append('\n');
    }
    return out.toString();
  }

  private static String value(MetadataValue value) {
    return switch (value) {
      case TextValue t -> t.value().replace("\n", "\\n");
      case IntegerValue i -> Long.toString(i.value());
      case DecimalValue d -> d.value().toPlainString();
      case RationalValue r -> r.toString();
      case BooleanValue b -> Boolean.toString(b.value());
      case LocalDateTimeValue l -> l.value().toString();
      case OffsetDateTimeValue o -> o.value().toString();
      case BinaryValue b -> "binary(" + b.size() + ")";
      case ListValue l ->
          l.values().stream()
              .map(ResultRenderer::value)
              .collect(Collectors.joining(", ", "[", "]"));
    };
  }
}
