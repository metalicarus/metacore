package br.com.genosha.metacore.format.image.png;

import br.com.genosha.metacore.exception.MetadataExtractionException;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.ParseContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Prepara um PNG para o metadata-extractor: percorre os chunks, descarta os dados da imagem (IDAT),
 * descarta texto compactado que descompactaria além de {@code maxDecompressedTextBytes} e, se o
 * arquivo terminar antes do IEND, devolve os chunks completos com um IEND sintético e um aviso
 * {@code TRUNCATED}. O metadata-extractor, sozinho, falha por inteiro em arquivos truncados e
 * descompacta texto sem limite.
 */
final class PngChunkScanner {

  static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] IEND = {
    0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82
  };

  /**
   * Chunks maiores que isto (fora o IDAT) são descartados: nenhum metadado legítimo chega perto.
   */
  static final int MAX_CHUNK_KEPT = 16 * 1024 * 1024;

  private PngChunkScanner() {}

  /** Chunks a entregar ao metadata-extractor; vazio quando nenhum chunk completo foi lido. */
  static Optional<byte[]> scan(InputStream in, ParseContext context) throws IOException {
    if (!Arrays.equals(in.readNBytes(SIGNATURE.length), SIGNATURE)) {
      throw new MetadataExtractionException("Missing PNG signature");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(SIGNATURE);
    long offset = SIGNATURE.length;
    long maxInflated = context.limits().maxDecompressedTextBytes();
    int kept = 0;
    while (true) {
      context.checkCancelled();
      byte[] header = in.readNBytes(8);
      if (header.length < 8) {
        warn(context, WarningCode.TRUNCATED, "File ends before the IEND chunk", offset);
        break;
      }
      long length = uint32(header, 0);
      String type = new String(header, 4, 4, StandardCharsets.ISO_8859_1);
      if (length > Integer.MAX_VALUE) {
        warn(context, WarningCode.CORRUPTED_SEGMENT, "Invalid length for chunk " + type, offset);
        break;
      }
      if (type.equals("IDAT") || length > MAX_CHUNK_KEPT) {
        if (length > MAX_CHUNK_KEPT && !type.equals("IDAT")) {
          warn(context, WarningCode.LIMIT_REACHED, "Skipped oversized chunk " + type, offset);
        }
        if (skip(in, length + 4) < length + 4) {
          warn(context, WarningCode.TRUNCATED, "File ends inside chunk " + type, offset);
          break;
        }
        offset += 8 + length + 4;
        continue;
      }
      byte[] data = in.readNBytes((int) length);
      byte[] crc = in.readNBytes(4);
      if (data.length < length || crc.length < 4) {
        warn(context, WarningCode.TRUNCATED, "File ends inside chunk " + type, offset);
        break;
      }
      if (inflatesBeyond(type, data, maxInflated)) {
        warn(
            context,
            WarningCode.LIMIT_REACHED,
            "Skipped chunk " + type + " that decompresses beyond maxDecompressedTextBytes",
            offset);
      } else {
        out.write(header);
        out.write(data);
        out.write(crc);
        kept++;
      }
      offset += 8 + length + 4;
      if (type.equals("IEND")) {
        return Optional.of(out.toByteArray());
      }
    }
    if (kept == 0) {
      return Optional.empty();
    }
    out.write(IEND);
    return Optional.of(out.toByteArray());
  }

  /** Mede o texto compactado de zTXt, iTXt e iCCP sem descompactar além do limite. */
  static boolean inflatesBeyond(String type, byte[] data, long limit) {
    int keywordEnd = indexOf(data, 0, (byte) 0);
    if (keywordEnd < 0) {
      return false;
    }
    int compressedStart;
    switch (type) {
      case "zTXt", "iCCP" -> compressedStart = keywordEnd + 2;
      case "iTXt" -> {
        if (keywordEnd + 2 >= data.length || data[keywordEnd + 1] == 0) {
          return false; // texto não compactado
        }
        int languageEnd = indexOf(data, keywordEnd + 3, (byte) 0);
        int translatedEnd = languageEnd < 0 ? -1 : indexOf(data, languageEnd + 1, (byte) 0);
        if (translatedEnd < 0) {
          return false;
        }
        compressedStart = translatedEnd + 1;
      }
      default -> {
        return false;
      }
    }
    if (compressedStart > data.length) {
      return false;
    }
    Inflater inflater = new Inflater();
    try {
      inflater.setInput(data, compressedStart, data.length - compressedStart);
      byte[] buffer = new byte[8192];
      long total = 0;
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          return false;
        }
        total += count;
        if (total > limit) {
          return true;
        }
      }
      return false;
    } catch (DataFormatException e) {
      return false; // o metadata-extractor registra o erro do chunk
    } finally {
      inflater.end();
    }
  }

  private static long skip(InputStream in, long count) throws IOException {
    long skipped = 0;
    byte[] buffer = new byte[8192];
    while (skipped < count) {
      int read = in.read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
      if (read == -1) {
        break;
      }
      skipped += read;
    }
    return skipped;
  }

  private static int indexOf(byte[] data, int from, byte value) {
    for (int i = Math.max(0, from); i < data.length; i++) {
      if (data[i] == value) {
        return i;
      }
    }
    return -1;
  }

  private static long uint32(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFFL) << 24)
        | ((bytes[offset + 1] & 0xFFL) << 16)
        | ((bytes[offset + 2] & 0xFFL) << 8)
        | (bytes[offset + 3] & 0xFFL);
  }

  private static void warn(ParseContext context, WarningCode code, String message, long offset) {
    context.warn(code, message, "offset " + offset);
  }
}
