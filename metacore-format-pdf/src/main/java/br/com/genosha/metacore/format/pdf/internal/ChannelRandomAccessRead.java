package br.com.genosha.metacore.format.pdf.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadView;

/**
 * {@link RandomAccessRead} do PDFBox sobre o canal de um {@code InputSource}, com uma janela de
 * leitura em memória. O PDFBox lê muito byte a byte; sem a janela, cada leitura viraria uma chamada
 * ao canal.
 */
public final class ChannelRandomAccessRead implements RandomAccessRead {

  private static final int WINDOW = 64 * 1024;

  private final SeekableByteChannel channel;
  private final long length;
  private final ByteBuffer window = ByteBuffer.allocate(WINDOW);
  private long windowStart;
  private int windowLength;
  private long position;
  private boolean closed;

  public ChannelRandomAccessRead(SeekableByteChannel channel, long length) {
    this.channel = channel;
    this.length = length;
  }

  @Override
  public int read() throws IOException {
    ensureOpen();
    if (position >= length) {
      return -1;
    }
    ensureWindow();
    int value = window.get((int) (position - windowStart)) & 0xFF;
    position++;
    return value;
  }

  @Override
  public int read(byte[] buffer, int offset, int count) throws IOException {
    ensureOpen();
    if (count == 0) {
      return 0;
    }
    if (position >= length) {
      return -1;
    }
    int total = 0;
    while (total < count && position < length) {
      ensureWindow();
      int inWindow = (int) (windowStart + windowLength - position);
      int chunk = Math.min(count - total, inWindow);
      window.get((int) (position - windowStart), buffer, offset + total, chunk);
      position += chunk;
      total += chunk;
    }
    return total;
  }

  @Override
  public long getPosition() throws IOException {
    ensureOpen();
    return position;
  }

  @Override
  public void seek(long newPosition) throws IOException {
    ensureOpen();
    if (newPosition < 0) {
      throw new IOException("Invalid position " + newPosition);
    }
    position = Math.min(newPosition, length);
  }

  @Override
  public long length() throws IOException {
    ensureOpen();
    return length;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public boolean isEOF() throws IOException {
    ensureOpen();
    return position >= length;
  }

  @Override
  public RandomAccessReadView createView(long startPosition, long streamLength) throws IOException {
    ensureOpen();
    return new RandomAccessReadView(this, startPosition, streamLength);
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      channel.close();
    }
  }

  private void ensureWindow() throws IOException {
    if (position >= windowStart && position < windowStart + windowLength) {
      return;
    }
    channel.position(position);
    window.clear();
    while (window.hasRemaining()) {
      if (channel.read(window) == -1) {
        break;
      }
    }
    windowStart = position;
    windowLength = window.position();
    if (windowLength == 0) {
      throw new IOException("Unexpected end of input at offset " + position);
    }
  }

  private void ensureOpen() throws IOException {
    if (closed) {
      throw new IOException("Input is closed");
    }
  }
}
