package br.com.genosha.metacore.spi;

import br.com.genosha.metacore.internal.FileNames;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

/**
 * O que um detector recebe: o início do conteúdo (até {@code maxDetectionBytes}) e o nome declarado
 * do arquivo. O detector não tem acesso ao restante do conteúdo.
 */
public final class DetectionRequest {

  private final byte[] header;
  private final Optional<String> fileName;

  private DetectionRequest(byte[] header, Optional<String> fileName) {
    this.header = header;
    this.fileName = fileName;
  }

  /** Cria a requisição. O array é copiado. */
  public static DetectionRequest of(byte[] header, Optional<String> fileName) {
    return new DetectionRequest(header.clone(), Objects.requireNonNull(fileName, "fileName"));
  }

  /** Quantidade de bytes disponíveis. */
  public int length() {
    return header.length;
  }

  /** Visão somente leitura do início do conteúdo, posicionada no byte 0. */
  public ByteBuffer header() {
    return ByteBuffer.wrap(header).asReadOnlyBuffer();
  }

  /** Byte na posição informada, entre 0 e 255. */
  public int byteAt(int index) {
    return header[index] & 0xFF;
  }

  /**
   * Indica se o conteúdo começa com a assinatura. Cada valor é um byte sem sinal, de 0 a 255, para
   * que assinaturas como {@code 0xFF, 0xD8} não precisem de cast.
   */
  public boolean startsWith(int... signature) {
    return matchesAt(0, signature);
  }

  /** Indica se a assinatura aparece a partir do offset informado. */
  public boolean matchesAt(int offset, int... signature) {
    if (offset < 0 || offset + signature.length > header.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (byteAt(offset + i) != signature[i]) {
        return false;
      }
    }
    return true;
  }

  /** Nome declarado do arquivo, quando conhecido. */
  public Optional<String> fileName() {
    return fileName;
  }

  /** Extensão do nome declarado, em minúsculas e sem ponto. */
  public Optional<String> extension() {
    return fileName.flatMap(FileNames::extension);
  }
}
