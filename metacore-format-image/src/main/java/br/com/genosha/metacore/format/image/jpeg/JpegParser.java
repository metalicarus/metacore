package br.com.genosha.metacore.format.image.jpeg;

import br.com.genosha.metacore.format.image.ImageFormats;
import br.com.genosha.metacore.format.image.internal.DrewMetadataEmitter;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.ParseContext;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegSegmentData;
import com.drew.metadata.Metadata;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Extrai metadados de JPEG com o metadata-extractor: estrutura (SOF), JFIF, EXIF, GPS, XMP, IPTC,
 * ICC e demais segmentos que a biblioteca entende. A leitura para no início dos dados da imagem
 * (SOS), então o tamanho do arquivo pouco influencia o custo.
 *
 * <p>Os segmentos são lidos pelo {@link JpegSegmentScanner}, que tolera arquivos truncados, e só os
 * completos são entregues ao metadata-extractor.
 */
public final class JpegParser implements MetadataParser {

  @Override
  public String id() {
    return "jpeg";
  }

  @Override
  public Set<String> supportedFormats() {
    return Set.of(ImageFormats.JPEG.id());
  }

  @Override
  public void parse(InputSource source, ParseContext context) throws IOException {
    JpegSegmentData segments;
    try (InputStream in = new BufferedInputStream(source.openStream())) {
      segments = JpegSegmentScanner.scan(in, context);
    }
    Metadata metadata = new Metadata();
    JpegMetadataReader.processJpegSegmentData(metadata, JpegMetadataReader.ALL_READERS, segments);
    DrewMetadataEmitter.emit(metadata, context);
  }
}
