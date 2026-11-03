package br.com.genosha.metacore.format.image.png;

import br.com.genosha.metacore.exception.MetadataExtractionException;
import br.com.genosha.metacore.format.image.ImageFormats;
import br.com.genosha.metacore.format.image.internal.DrewMetadataEmitter;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.ParseContext;
import com.drew.imaging.png.PngMetadataReader;
import com.drew.imaging.png.PngProcessingException;
import com.drew.metadata.Metadata;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * Extrai metadados de PNG com o metadata-extractor: IHDR, texto ({@code tEXt}, {@code zTXt}, {@code
 * iTXt}), XMP, EXIF ({@code eXIf}), ICC, tempo de modificação e demais chunks auxiliares. Os chunks
 * passam antes pelo {@link PngChunkScanner}.
 */
public final class PngParser implements MetadataParser {

  @Override
  public String id() {
    return "png";
  }

  @Override
  public Set<String> supportedFormats() {
    return Set.of(ImageFormats.PNG.id());
  }

  @Override
  public void parse(InputSource source, ParseContext context) throws IOException {
    Optional<byte[]> chunks;
    try (InputStream in = new BufferedInputStream(source.openStream())) {
      chunks = PngChunkScanner.scan(in, context);
    }
    if (chunks.isEmpty()) {
      return; // o scanner já registrou o motivo
    }
    Metadata metadata;
    try {
      metadata = PngMetadataReader.readMetadata(new ByteArrayInputStream(chunks.get()));
    } catch (PngProcessingException e) {
      throw new MetadataExtractionException("Invalid PNG structure: " + e.getMessage(), e);
    }
    DrewMetadataEmitter.emit(metadata, context);
  }
}
