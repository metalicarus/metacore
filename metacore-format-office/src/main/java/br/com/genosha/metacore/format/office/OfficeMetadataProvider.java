package br.com.genosha.metacore.format.office;

import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import java.util.List;

/** Provedor do módulo Office, registrado via {@link java.util.ServiceLoader}. */
public final class OfficeMetadataProvider implements MetadataProvider {

  @Override
  public List<FileFormat> formats() {
    return List.of(OfficeFormats.ZIP, OfficeFormats.DOCX, OfficeFormats.XLSX, OfficeFormats.PPTX);
  }

  @Override
  public List<FormatDetector> detectors() {
    return List.of(new OoxmlDetector());
  }

  @Override
  public List<MetadataParser> parsers() {
    return List.of(new OoxmlParser());
  }
}
