package br.com.genosha.metacore.internal.io;

import br.com.genosha.metacore.io.InputSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.Optional;
import java.util.OptionalLong;

/** Fonte apoiada num array em memória. O array não é copiado: quem cria não pode alterá-lo. */
public final class ByteArrayInputSource implements InputSource {

  private final byte[] content;
  private final int length;
  private final Optional<String> fileName;

  public ByteArrayInputSource(byte[] content, Optional<String> fileName) {
    this(content, content.length, fileName);
  }

  /** Fonte que usa só os primeiros {@code length} bytes do array. */
  public ByteArrayInputSource(byte[] content, int length, Optional<String> fileName) {
    if (length < 0 || length > content.length) {
      throw new IllegalArgumentException("Invalid length: " + length);
    }
    this.content = content;
    this.length = length;
    this.fileName = fileName;
  }

  @Override
  public Optional<String> fileName() {
    return fileName;
  }

  @Override
  public OptionalLong size() {
    return OptionalLong.of(length);
  }

  @Override
  public InputStream openStream() {
    return new ByteArrayInputStream(content, 0, length);
  }

  @Override
  public SeekableByteChannel openChannel() {
    return new ByteArrayChannel(content, length);
  }
}
