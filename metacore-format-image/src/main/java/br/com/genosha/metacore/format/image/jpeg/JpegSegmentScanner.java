package br.com.genosha.metacore.format.image.jpeg;

import br.com.genosha.metacore.exception.MetadataExtractionException;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.ParseContext;
import com.drew.imaging.jpeg.JpegSegmentData;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lê os segmentos de um JPEG até o início dos dados da imagem (SOS), de forma tolerante: um arquivo
 * truncado ou corrompido devolve os segmentos completos lidos até ali e um aviso, em vez de falhar
 * por inteiro como o leitor do metadata-extractor.
 *
 * <p>Truncamento dentro dos dados da imagem (depois do SOS) não é detectado: os metadados já foram
 * todos lidos nesse ponto.
 */
final class JpegSegmentScanner {

  private static final int SOI = 0xD8;
  private static final int EOI = 0xD9;
  private static final int SOS = 0xDA;
  private static final int TEM = 0x01;

  private JpegSegmentScanner() {}

  static JpegSegmentData scan(InputStream in, ParseContext context) throws IOException {
    CountingReader reader = new CountingReader(in);
    if (reader.read() != 0xFF || reader.read() != SOI) {
      throw new MetadataExtractionException("Missing JPEG start-of-image marker");
    }
    JpegSegmentData segments = new JpegSegmentData();
    while (true) {
      context.checkCancelled();
      long markerOffset = reader.offset();
      int prefix = reader.read();
      if (prefix == -1) {
        truncated(context, "File ends before the start of image data", markerOffset);
        return segments;
      }
      if (prefix != 0xFF) {
        corrupted(context, "Expected a segment marker", markerOffset);
        return segments;
      }
      int marker = reader.read();
      while (marker == 0xFF) {
        marker = reader.read(); // bytes de preenchimento antes do código do marcador
      }
      if (marker == -1) {
        truncated(context, "File ends inside a segment marker", markerOffset);
        return segments;
      }
      if (marker == SOS || marker == EOI) {
        return segments;
      }
      if (marker == TEM || (marker >= 0xD0 && marker <= 0xD7)) {
        continue; // marcadores sem tamanho
      }
      int high = reader.read();
      int low = reader.read();
      if (low == -1) {
        truncated(context, "File ends inside the length of segment " + hex(marker), markerOffset);
        return segments;
      }
      int length = (high << 8) | low;
      if (length < 2) {
        corrupted(
            context, "Invalid length " + length + " for segment " + hex(marker), markerOffset);
        return segments;
      }
      byte[] payload = reader.readNBytes(length - 2);
      if (payload.length < length - 2) {
        truncated(context, "File ends inside segment " + hex(marker), markerOffset);
        return segments;
      }
      segments.addSegment((byte) marker, payload);
    }
  }

  private static void truncated(ParseContext context, String message, long offset) {
    context.warn(WarningCode.TRUNCATED, message, "offset " + offset);
  }

  private static void corrupted(ParseContext context, String message, long offset) {
    context.warn(WarningCode.CORRUPTED_SEGMENT, message, "offset " + offset);
  }

  private static String hex(int marker) {
    return String.format("0x%02X", marker);
  }

  /** Leitura byte a byte com contagem de posição. */
  private static final class CountingReader {
    private final InputStream in;
    private long offset;

    CountingReader(InputStream in) {
      this.in = in;
    }

    int read() throws IOException {
      int value = in.read();
      if (value != -1) {
        offset++;
      }
      return value;
    }

    byte[] readNBytes(int length) throws IOException {
      byte[] bytes = in.readNBytes(length);
      offset += bytes.length;
      return bytes;
    }

    long offset() {
      return offset;
    }
  }
}
