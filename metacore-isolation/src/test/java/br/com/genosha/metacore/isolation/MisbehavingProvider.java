package br.com.genosha.metacore.isolation;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.spi.ParseContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Formato de teste cujo conteúdo diz ao parser como se comportar mal: {@code MCTEST:<ação>\n}. Só
 * roda dentro do worker, descoberto por {@code ServiceLoader}.
 */
public final class MisbehavingProvider implements MetadataProvider {

  static final String MAGIC = "MCTEST:";
  static final FileFormat FORMAT = FileFormat.of("mctest", "application/x-mctest", "MCTEST");

  /** Conteúdo que pede a ação ao parser. */
  static byte[] content(String action) {
    return (MAGIC + action + "\n").getBytes(StandardCharsets.US_ASCII);
  }

  @Override
  public List<FileFormat> formats() {
    return List.of(FORMAT);
  }

  @Override
  public List<FormatDetector> detectors() {
    return List.of(new Detector());
  }

  @Override
  public List<MetadataParser> parsers() {
    return List.of(new Parser());
  }

  private static final class Detector implements FormatDetector {
    @Override
    public String id() {
      return "mctest-magic";
    }

    @Override
    public Optional<DetectionCandidate> detect(DetectionRequest request) {
      byte[] magic = MAGIC.getBytes(StandardCharsets.US_ASCII);
      int[] signature = new int[magic.length];
      for (int i = 0; i < magic.length; i++) {
        signature[i] = magic[i];
      }
      return request.startsWith(signature)
          ? Optional.of(DetectionCandidate.of(FORMAT.id(), Confidence.EXACT, Evidence.MAGIC))
          : Optional.empty();
    }
  }

  private static final class Parser implements MetadataParser {
    @Override
    public String id() {
      return "mctest";
    }

    @Override
    public Set<String> supportedFormats() {
      return Set.of(FORMAT.id());
    }

    @Override
    public void parse(InputSource source, ParseContext context) throws IOException {
      String action;
      try (InputStream in = source.openStream()) {
        String header = new String(in.readNBytes(256), StandardCharsets.US_ASCII);
        action = header.substring(MAGIC.length(), header.indexOf('\n'));
      }
      switch (action) {
        case "pid" ->
            context.emit(
                MetadataEntry.raw(
                    "MCTEST", "pid", MetadataValue.of(ProcessHandle.current().pid())));
        case "stdout" -> {
          System.out.println("noise that must not reach the protocol");
          System.out.flush();
          context.emit(MetadataEntry.raw("MCTEST", "printed", MetadataValue.of(true)));
        }
        case "hang" -> {
          while (true) {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              // ignora, como uma biblioteca que não coopera
            }
          }
        }
        case "oom" -> {
          List<byte[]> hog = new ArrayList<>();
          while (true) {
            hog.add(new byte[1024 * 1024]);
          }
        }
        case "exit" -> System.exit(42);
        default -> throw new IOException("Unknown action " + action);
      }
    }
  }
}
