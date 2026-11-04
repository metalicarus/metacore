package br.com.genosha.metacore.internal.io;

import br.com.genosha.metacore.io.InputSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.OptionalLong;

/** Fonte apoiada num arquivo, lido sob demanda. */
public final class PathInputSource implements InputSource {

  private final Path file;
  private final Optional<String> fileName;
  private final long size;

  public PathInputSource(Path file) throws IOException {
    this(file, Optional.ofNullable(file.getFileName()).map(Path::toString));
  }

  /** Fonte com nome declarado diferente do nome real, usada pelo spool em arquivo temporário. */
  public PathInputSource(Path file, Optional<String> fileName) throws IOException {
    this.file = file;
    this.fileName = fileName;
    this.size = Files.size(file);
  }

  @Override
  public Optional<String> fileName() {
    return fileName;
  }

  @Override
  public OptionalLong size() {
    return OptionalLong.of(size);
  }

  @Override
  public InputStream openStream() throws IOException {
    return Files.newInputStream(file, StandardOpenOption.READ);
  }

  @Override
  public SeekableByteChannel openChannel() throws IOException {
    return Files.newByteChannel(file, StandardOpenOption.READ);
  }
}
