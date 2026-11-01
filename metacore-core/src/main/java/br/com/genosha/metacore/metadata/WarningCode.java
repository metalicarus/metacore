package br.com.genosha.metacore.metadata;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Código estável de um aviso. O código é o contrato; a mensagem do aviso pode mudar entre versões.
 * Módulos de terceiros podem criar códigos próprios.
 *
 * @param value o código, em maiúsculas com sublinhado
 * @param dataLoss indica se o problema fez parte dos metadados deixar de ser lida; avisos assim
 *     tornam o resultado {@code PARTIAL}
 */
public record WarningCode(String value, boolean dataLoss) {

  // Precisa vir antes das constantes: elas passam pela validação do construtor.
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

  public static final WarningCode EXTENSION_MISMATCH = new WarningCode("EXTENSION_MISMATCH", false);
  public static final WarningCode AMBIGUOUS_DETECTION =
      new WarningCode("AMBIGUOUS_DETECTION", false);
  public static final WarningCode DETECTOR_ERROR = new WarningCode("DETECTOR_ERROR", false);
  public static final WarningCode NO_PARSER = new WarningCode("NO_PARSER", false);
  public static final WarningCode TRUNCATED = new WarningCode("TRUNCATED", true);
  public static final WarningCode CORRUPTED_SEGMENT = new WarningCode("CORRUPTED_SEGMENT", true);
  public static final WarningCode LIMIT_REACHED = new WarningCode("LIMIT_REACHED", true);
  public static final WarningCode ENCRYPTED = new WarningCode("ENCRYPTED", true);
  public static final WarningCode VALUE_CONVERSION_FAILED =
      new WarningCode("VALUE_CONVERSION_FAILED", false);
  public static final WarningCode METADATA_CONFLICT = new WarningCode("METADATA_CONFLICT", false);
  public static final WarningCode PARSER_ERROR = new WarningCode("PARSER_ERROR", true);
  public static final WarningCode CANCELLED = new WarningCode("CANCELLED", true);

  public WarningCode {
    Objects.requireNonNull(value, "value");
    if (!CODE.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid warning code: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
