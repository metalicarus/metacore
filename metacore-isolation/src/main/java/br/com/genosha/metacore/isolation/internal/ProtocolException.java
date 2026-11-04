package br.com.genosha.metacore.isolation.internal;

import java.io.IOException;
import java.io.Serial;

/** Mensagem do worker fora do protocolo. */
public final class ProtocolException extends IOException {

  @Serial private static final long serialVersionUID = 1L;

  public ProtocolException(String message) {
    super(message);
  }
}
