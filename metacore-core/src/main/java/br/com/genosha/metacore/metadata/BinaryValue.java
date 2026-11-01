package br.com.genosha.metacore.metadata;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Dado binário, como uma miniatura ou uma MakerNote. O tamanho é sempre conhecido; o conteúdo só é
 * guardado até o limite {@code maxBinaryValueBytes} da análise.
 */
public final class BinaryValue implements MetadataValue {

  private final long size;
  private final byte[] content;

  private BinaryValue(long size, byte[] content) {
    if (size < 0) {
      throw new IllegalArgumentException("Size must not be negative");
    }
    this.size = size;
    this.content = content;
  }

  /** Valor com conteúdo. O array é copiado. */
  public static BinaryValue of(byte[] content) {
    return new BinaryValue(content.length, content.clone());
  }

  /** Valor sem conteúdo, só com o tamanho em bytes. */
  public static BinaryValue sizeOnly(long size) {
    return new BinaryValue(size, null);
  }

  /** Tamanho do dado original em bytes. */
  public long size() {
    return size;
  }

  /** Cópia do conteúdo, quando guardado. */
  public Optional<byte[]> content() {
    return Optional.ofNullable(content).map(byte[]::clone);
  }

  /** Indica se o conteúdo foi guardado. */
  public boolean hasContent() {
    return content != null;
  }

  @Override
  public ValueType type() {
    return ValueType.BINARY;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BinaryValue that
        && size == that.size
        && Arrays.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return 31 * Long.hashCode(size) + Arrays.hashCode(content);
  }

  @Override
  public String toString() {
    if (content == null) {
      return "BinaryValue[size=" + size + "]";
    }
    int shown = Math.min(content.length, 16);
    String prefix = HexFormat.of().formatHex(content, 0, shown);
    return "BinaryValue[size=" + size + ", content=" + prefix + (shown < size ? "...]" : "]");
  }
}
