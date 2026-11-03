package br.com.genosha.metacore.format.pdf;

import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import java.util.List;

/** Provedor do módulo de PDF, registrado via {@link java.util.ServiceLoader}. */
public final class PdfMetadataProvider implements MetadataProvider {

  @Override
  public List<FileFormat> formats() {
    return List.of(PdfFormats.PDF);
  }

  @Override
  public List<FormatDetector> detectors() {
    return List.of(new PdfDetector());
  }

  @Override
  public List<MetadataParser> parsers() {
    return List.of(new PdfParser());
  }
}
