package br.com.genosha.metacore.internal.io;

import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.io.InputSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fonte sobre um {@link InputStream} lido sob demanda (ADR 0015). Só os bytes que algum leitor
 * pediu são lidos do stream e guardados: em memória até o limiar, num arquivo temporário acima
 * dele. Cada {@link #openStream()} começa do início, repetindo o que já foi guardado e continuando
 * do stream original. {@link #openChannel()} precisa do conteúdo inteiro e lê o resto do stream.
 *
 * <p>Um JPEG, por exemplo, tem só o cabeçalho e os segmentos de metadados lidos; os dados da imagem
 * nunca saem do stream. O stream do chamador não é fechado.
 */
public final class StreamInputSource implements InputSource, Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(StreamInputSource.class);

  /**
   * Tamanho dos blocos em memória: arquivos pequenos alocam pouco e nada é recopiado ao crescer.
   */
  private static final int BLOCK = 16 * 1024;

  private final InputStream in;
  private final Optional<String> fileName;
  private final int memoryThreshold;
  private final long maxSize;

  private final List<byte[]> blocks = new ArrayList<>();
  private int memoryLength;
  private byte[] transfer;
  private Path overflowFile;
  private FileChannel overflow;
  private long overflowLength;
  private boolean endOfStream;

  public StreamInputSource(
      InputStream in, Optional<String> fileName, int memoryThreshold, long maxSize) {
    this.in = in;
    this.fileName = fileName;
    this.memoryThreshold = memoryThreshold;
    this.maxSize = maxSize;
  }

  @Override
  public Optional<String> fileName() {
    return fileName;
  }

  /** Conhecido só depois que o stream foi lido até o fim. */
  @Override
  public OptionalLong size() {
    return endOfStream ? OptionalLong.of(recorded()) : OptionalLong.empty();
  }

  @Override
  public InputStream openStream() {
    return new ReplayStream();
  }

  @Override
  public SeekableByteChannel openChannel() throws IOException {
    while (fetch()) {
      // acesso aleatório precisa do conteúdo inteiro
    }
    return new RecordedChannel();
  }

  @Override
  public void close() {
    if (overflow == null) {
      return;
    }
    try {
      overflow.close();
      Files.deleteIfExists(overflowFile);
    } catch (IOException e) {
      LOG.warn("Could not delete temporary spool file {}", overflowFile, e);
    }
  }

  private long recorded() {
    return memoryLength + overflowLength;
  }

  /**
   * Lê mais um trecho do stream e o guarda: direto no bloco de memória atual enquanto houver espaço
   * abaixo do limiar, no arquivo temporário depois disso. Devolve {@code false} no fim do stream.
   */
  private boolean fetch() throws IOException {
    if (endOfStream) {
      return false;
    }
    if (memoryLength < memoryThreshold) {
      int blockOffset = memoryLength % BLOCK;
      if (blockOffset == 0) {
        blocks.add(new byte[BLOCK]);
      }
      int room = Math.min(BLOCK - blockOffset, memoryThreshold - memoryLength);
      int read = in.read(blocks.get(blocks.size() - 1), blockOffset, room);
      if (read == -1) {
        endOfStream = true;
        return false;
      }
      checkLimit(read);
      memoryLength += read;
      return true;
    }
    if (transfer == null) {
      transfer = new byte[BLOCK];
    }
    int read = in.read(transfer);
    if (read == -1) {
      endOfStream = true;
      return false;
    }
    checkLimit(read);
    if (overflow == null) {
      overflowFile = Files.createTempFile("metacore-", ".spool");
      overflow = FileChannel.open(overflowFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }
    ByteBuffer data = ByteBuffer.wrap(transfer, 0, read);
    while (data.hasRemaining()) {
      overflowLength += overflow.write(data, overflowLength);
    }
    return true;
  }

  private void checkLimit(int read) {
    if (recorded() + read > maxSize) {
      throw new InputLimitExceededException("Input exceeds maxFileSize of " + maxSize + " bytes");
    }
  }

  /**
   * Copia até {@code length} bytes a partir de {@code position}, lendo do stream se preciso.
   * Devolve -1 no fim do conteúdo.
   */
  private int readAt(long position, byte[] target, int offset, int length) throws IOException {
    while (position >= recorded()) {
      if (!fetch()) {
        return -1;
      }
    }
    if (position < memoryLength) {
      int block = (int) (position / BLOCK);
      int blockOffset = (int) (position % BLOCK);
      int count = (int) Math.min(length, Math.min(BLOCK - blockOffset, memoryLength - position));
      System.arraycopy(blocks.get(block), blockOffset, target, offset, count);
      return count;
    }
    long inOverflow = position - memoryLength;
    int count = (int) Math.min(length, overflowLength - inOverflow);
    ByteBuffer buffer = ByteBuffer.wrap(target, offset, count);
    while (buffer.hasRemaining()) {
      overflow.read(buffer, inOverflow + buffer.position() - offset);
    }
    return count;
  }

  /** Leitura sequencial a partir do início; fechar não fecha o stream original. */
  private final class ReplayStream extends InputStream {
    private final byte[] one = new byte[1];
    private long position;

    @Override
    public int read() throws IOException {
      return read(one, 0, 1) == -1 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      int count = readAt(position, target, offset, length);
      if (count > 0) {
        position += count;
      }
      return count;
    }

    @Override
    public long skip(long count) throws IOException {
      // Os bytes pulados também são guardados: outro leitor pode precisar deles depois.
      long target = position + Math.max(0, count);
      while (recorded() < target && fetch()) {
        // lê até alcançar a posição
      }
      long skipped = Math.min(target, recorded()) - position;
      position += skipped;
      return skipped;
    }
  }

  /** Canal somente leitura sobre o conteúdo já guardado por inteiro. */
  private final class RecordedChannel implements SeekableByteChannel {
    private final byte[] buffer = new byte[BLOCK];
    private long position;
    private boolean open = true;

    @Override
    public int read(ByteBuffer destination) throws IOException {
      ensureOpen();
      if (!destination.hasRemaining()) {
        return 0;
      }
      int count = readAt(position, buffer, 0, Math.min(destination.remaining(), buffer.length));
      if (count > 0) {
        destination.put(buffer, 0, count);
        position += count;
      }
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
      return recorded();
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
}
