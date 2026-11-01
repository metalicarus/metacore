package br.com.genosha.metacore.api;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.testing.Fakes;
import java.util.List;

/** Registrado em {@code META-INF/services} dos testes para exercitar a descoberta. */
public class DiscoverableTestProvider implements MetadataProvider {

  static final FileFormat GAMMA = FileFormat.of("gamma", "application/x-gamma", "Gamma", "gam");
  static final byte[] GAMMA_CONTENT = {'G', 'A', 'M', 'M', 'A'};

  @Override
  public List<FileFormat> formats() {
    return List.of(GAMMA);
  }

  @Override
  public List<FormatDetector> detectors() {
    return List.of(Fakes.magic("gamma-magic", "gamma", Confidence.EXACT, 'G', 'A', 'M', 'M', 'A'));
  }
}
