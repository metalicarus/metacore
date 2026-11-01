package br.com.genosha.metacore.testkit;

import br.com.genosha.metacore.format.Confidence;
import br.com.genosha.metacore.format.Evidence;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.MetadataValue;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.DetectionCandidate;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataEntry;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import br.com.genosha.metacore.spi.ParseContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prova que a suíte roda contra um provedor simples: formato "tlv" com assinatura {@code TLV1}
 * seguida de pares (tamanho, texto).
 */
class FakeProviderContractTest extends MetadataParserContractTest {

  private static final FileFormat TLV = FileFormat.of("tlv", "application/x-tlv", "TLV", "tlv");

  @TempDir static Path dir;

  @Override
  protected MetadataProvider provider() {
    return new MetadataProvider() {
      @Override
      public List<FileFormat> formats() {
        return List.of(TLV);
      }

      @Override
      public List<FormatDetector> detectors() {
        return List.of(new TlvDetector());
      }

      @Override
      public List<MetadataParser> parsers() {
        return List.of(new TlvParser());
      }
    };
  }

  @Override
  protected List<ContractFixture> fixtures() {
    try {
      byte[] content = {'T', 'L', 'V', '1', 2, 'h', 'i', 3, 'a', 'b', 'c'};
      return List.of(ContractFixture.of(Files.write(dir.resolve("sample.tlv"), content), "tlv"));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class TlvDetector implements FormatDetector {
    @Override
    public String id() {
      return "tlv-magic";
    }

    @Override
    public Optional<DetectionCandidate> detect(DetectionRequest request) {
      return request.startsWith('T', 'L', 'V', '1')
          ? Optional.of(DetectionCandidate.of("tlv", Confidence.EXACT, Evidence.MAGIC))
          : Optional.empty();
    }
  }

  private static final class TlvParser implements MetadataParser {
    @Override
    public String id() {
      return "tlv";
    }

    @Override
    public Set<String> supportedFormats() {
      return Set.of("tlv");
    }

    @Override
    public void parse(InputSource source, ParseContext context) throws IOException {
      try (InputStream in = source.openStream()) {
        in.skipNBytes(4);
        int index = 0;
        int length;
        while ((length = in.read()) != -1) {
          context.checkCancelled();
          byte[] value = in.readNBytes(length);
          if (value.length < length) {
            context.warn(WarningCode.TRUNCATED, "Field ended early", "field " + index);
            return;
          }
          context.emit(
              MetadataEntry.raw("TLV", "Field " + index, MetadataValue.of(new String(value))));
          index++;
        }
      }
    }
  }
}
