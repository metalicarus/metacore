package br.com.genosha.metacore.testkit;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Arquivo válido usado pela suíte de contrato.
 *
 * @param file arquivo de teste
 * @param formatId formato que a detecção deve identificar
 */
public record ContractFixture(Path file, String formatId) {

  public ContractFixture {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(formatId, "formatId");
  }

  public static ContractFixture of(Path file, String formatId) {
    return new ContractFixture(file, formatId);
  }

  @Override
  public String toString() {
    return file.getFileName() + " (" + formatId + ")";
  }
}
