package br.com.genosha.metacore.io;

import br.com.genosha.metacore.internal.io.ByteArrayInputSource;
import br.com.genosha.metacore.internal.io.PathInputSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Conteúdo a analisar, com leitura sequencial e acesso aleatório. Cada chamada de {@link
 * #openStream()} ou {@link #openChannel()} começa do início, de forma independente, e quem abre é
 * quem fecha. Nenhuma das duas permite alterar o conteúdo.
 */
public interface InputSource {

  /** Nome declarado do arquivo, sem diretórios, quando conhecido. */
  Optional<String> fileName();

  /** Tamanho do conteúdo em bytes. */
  long size();

  /** Abre um fluxo sequencial a partir do início. */
  InputStream openStream() throws IOException;

  /** Abre um canal somente leitura com acesso aleatório. */
  SeekableByteChannel openChannel() throws IOException;

  /** Fonte apoiada num arquivo. O arquivo é lido sob demanda, sem cópia. */
  static InputSource of(Path file) throws IOException {
    return new PathInputSource(file);
  }

  /** Fonte apoiada num array em memória. O array é copiado. */
  static InputSource of(byte[] content, String fileName) {
    return new ByteArrayInputSource(content.clone(), Optional.ofNullable(fileName));
  }
}
