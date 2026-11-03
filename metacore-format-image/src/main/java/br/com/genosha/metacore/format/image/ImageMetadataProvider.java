package br.com.genosha.metacore.format.image;

import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.format.image.jpeg.JpegDetector;
import br.com.genosha.metacore.format.image.jpeg.JpegParser;
import br.com.genosha.metacore.format.image.png.PngDetector;
import br.com.genosha.metacore.format.image.png.PngParser;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import java.util.List;

/** Provedor do módulo de imagem, registrado via {@link java.util.ServiceLoader}. */
public final class ImageMetadataProvider implements MetadataProvider {

  @Override
  public List<FileFormat> formats() {
    return List.of(ImageFormats.JPEG, ImageFormats.PNG);
  }

  @Override
  public List<FormatDetector> detectors() {
    return List.of(new JpegDetector(), new PngDetector());
  }

  @Override
  public List<MetadataParser> parsers() {
    return List.of(new JpegParser(), new PngParser());
  }
}
