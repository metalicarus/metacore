package br.com.genosha.metacore.metadata;

import java.util.Objects;
import java.util.Optional;

/**
 * Aviso produzido durante a análise.
 *
 * @param code código estável
 * @param message mensagem legível
 * @param location onde o problema ocorreu (offset, segmento ou chave), quando conhecido
 */
public record Warning(WarningCode code, String message, Optional<String> location) {

  public Warning {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(location, "location");
  }

  public static Warning of(WarningCode code, String message) {
    return new Warning(code, message, Optional.empty());
  }

  public static Warning of(WarningCode code, String message, String location) {
    return new Warning(code, message, Optional.of(location));
  }
}
