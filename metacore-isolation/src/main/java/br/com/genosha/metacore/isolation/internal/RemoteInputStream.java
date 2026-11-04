package br.com.genosha.metacore.isolation.internal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Lado do worker de um {@code InputStream} de quem chamou: cada bloco é pedido ao pai só quando o
 * parser precisa dele (ADR 0015 e 0016).
 */
final class RemoteInputStream extends InputStream {

  private final DataInputStream in;
  private final DataOutputStream out;
  private final byte[] buffer = new byte[Protocol.CHUNK];
  private int position;
  private int limit;
  private boolean eof;
  private String failure;

  RemoteInputStream(DataInputStream in, DataOutputStream out) {
    this.in = in;
    this.out = out;
  }

  @Override
  public int read() throws IOException {
    if (!fill()) {
      return -1;
    }
    return buffer[position++] & 0xFF;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    Objects.checkFromIndexSize(offset, length, target.length);
    if (length == 0) {
      return 0;
    }
    if (!fill()) {
      return -1;
    }
    int count = Math.min(length, limit - position);
    System.arraycopy(buffer, position, target, offset, count);
    position += count;
    return count;
  }

  @Override
  public int available() {
    return limit - position;
  }

  private boolean fill() throws IOException {
    if (position < limit) {
      return true;
    }
    if (failure != null) {
      throw new IOException(failure);
    }
    if (eof) {
      return false;
    }
    out.writeByte(Protocol.READ);
    out.writeInt(Protocol.CHUNK);
    out.flush();
    int count = in.readInt();
    if (count == Protocol.EOF) {
      eof = true;
      return false;
    }
    if (count == Protocol.SOURCE_ERROR) {
      failure = "Input stream failed: " + Protocol.readMessage(in);
      throw new IOException(failure);
    }
    if (count <= 0 || count > Protocol.CHUNK) {
      throw new ProtocolException("Invalid chunk of " + count + " bytes");
    }
    in.readFully(buffer, 0, count);
    position = 0;
    limit = count;
    return true;
  }
}
