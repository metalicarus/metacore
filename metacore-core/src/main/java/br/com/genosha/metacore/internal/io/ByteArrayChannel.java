package br.com.genosha.metacore.internal.io;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/** Canal somente leitura sobre um array. */
final class ByteArrayChannel implements SeekableByteChannel {

  private final byte[] content;
  private final int length;
  private long position;
  private boolean open = true;

  ByteArrayChannel(byte[] content, int length) {
    this.content = content;
    this.length = length;
  }

  @Override
  public int read(ByteBuffer destination) throws ClosedChannelException {
    ensureOpen();
    if (position >= length) {
      return -1;
    }
    int count = (int) Math.min(destination.remaining(), length - position);
    destination.put(content, (int) position, count);
    position += count;
    return count;
  }

  @Override
  public int write(ByteBuffer source) {
    throw new NonWritableChannelException();
  }

  @Override
  public long position() throws ClosedChannelException {
    ensureOpen();
    return position;
  }

  @Override
  public SeekableByteChannel position(long newPosition) throws ClosedChannelException {
    ensureOpen();
    if (newPosition < 0) {
      throw new IllegalArgumentException("Negative position: " + newPosition);
    }
    position = newPosition;
    return this;
  }

  @Override
  public long size() throws ClosedChannelException {
    ensureOpen();
    return length;
  }

  @Override
  public SeekableByteChannel truncate(long size) {
    throw new NonWritableChannelException();
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }

  private void ensureOpen() throws ClosedChannelException {
    if (!open) {
      throw new ClosedChannelException();
    }
  }
}
