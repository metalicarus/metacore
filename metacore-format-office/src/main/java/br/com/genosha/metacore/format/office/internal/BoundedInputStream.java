package br.com.genosha.metacore.format.office.internal;

import java.io.IOException;
import java.io.InputStream;

/** Lê no máximo {@code limit} bytes do stream de baixo, sem fechá-lo. */
final class BoundedInputStream extends InputStream {

  private final InputStream in;
  private long left;

  BoundedInputStream(InputStream in, long limit) {
    this.in = in;
    this.left = limit;
  }

  /** Quanto ainda pode ser lido. */
  long left() {
    return left;
  }

  @Override
  public int read() throws IOException {
    if (left <= 0) {
      return -1;
    }
    int value = in.read();
    if (value >= 0) {
      left--;
    }
    return value;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (left <= 0) {
      return -1;
    }
    int read = in.read(b, off, (int) Math.min(len, left));
    if (read > 0) {
      left -= read;
    }
    return read;
  }

  @Override
  public void close() {
    // o stream de baixo é de quem o abriu
  }
}
