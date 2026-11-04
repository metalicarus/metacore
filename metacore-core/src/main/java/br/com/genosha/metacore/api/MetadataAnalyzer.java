package br.com.genosha.metacore.api;

import br.com.genosha.metacore.exception.AnalysisCancelledException;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.format.DetectionResult;
import br.com.genosha.metacore.format.FileFormat;
import br.com.genosha.metacore.format.FormatRegistry;
import br.com.genosha.metacore.internal.DefaultParseContext;
import br.com.genosha.metacore.internal.DetectionResolver;
import br.com.genosha.metacore.internal.Failures;
import br.com.genosha.metacore.internal.FileNames;
import br.com.genosha.metacore.internal.ParserRegistry;
import br.com.genosha.metacore.internal.PrimarySelector;
import br.com.genosha.metacore.internal.io.PathInputSource;
import br.com.genosha.metacore.internal.io.StreamInputSource;
import br.com.genosha.metacore.io.InputSource;
import br.com.genosha.metacore.metadata.Metadata;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import br.com.genosha.metacore.spi.DetectionRequest;
import br.com.genosha.metacore.spi.FormatDetector;
import br.com.genosha.metacore.spi.MetadataParser;
import br.com.genosha.metacore.spi.MetadataProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fachada principal da biblioteca. Imutável e thread-safe: uma instância pode ser compartilhada
 * pela aplicação inteira. Não segura recursos entre análises.
 *
 * <p>Exceções só são lançadas quando a análise não pode nem começar (entrada inexistente, ilegível
 * ou acima de {@code maxFileSize}). Depois disso, todo problema vira estado e avisos no resultado.
 */
public final class MetadataAnalyzer {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataAnalyzer.class);

  private final AnalysisLimits limits;
  private final FormatRegistry formats;
  private final DetectionResolver resolver;
  private final ParserRegistry parsers;

  private MetadataAnalyzer(
      AnalysisLimits limits,
      FormatRegistry formats,
      List<FormatDetector> detectors,
      List<MetadataParser> parsers) {
    this.limits = limits;
    this.formats = formats;
    this.resolver = new DetectionResolver(detectors, formats);
    this.parsers = new ParserRegistry(parsers, formats);
  }

  /** Analisador com limites padrão e todos os provedores encontrados no classpath. */
  public static MetadataAnalyzer createDefault() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Limites aplicados a cada análise. */
  public AnalysisLimits limits() {
    return limits;
  }

  /** Formatos conhecidos por este analisador. */
  public FormatRegistry formats() {
    return formats;
  }

  /** Identifica e extrai os metadados de um arquivo. */
  public MetadataResult analyze(Path file) {
    return analyze(open(file));
  }

  /**
   * Identifica e extrai os metadados de um stream. O stream é lido sob demanda: só o que o parser
   * precisa sai dele (ADR 0015). O stream não é fechado.
   *
   * <p>Como o stream pode não ser lido até o fim, o tamanho em {@link FileInfo#size()} pode ficar
   * vazio, e {@code maxFileSize} limita quanto é lido, e não o tamanho total do stream.
   *
   * @param fileName nome declarado do conteúdo, usado só como evidência de extensão; pode ser nulo
   */
  public MetadataResult analyze(InputStream in, String fileName) {
    Objects.requireNonNull(in, "in");
    try (StreamInputSource source =
        new StreamInputSource(
            in,
            Optional.ofNullable(fileName),
            limits.spoolMemoryThreshold(),
            limits.maxFileSize())) {
      return run(source);
    }
  }

  /** Identifica e extrai os metadados de uma fonte. */
  public MetadataResult analyze(InputSource source) {
    checkSize(source);
    return run(source);
  }

  /** Identifica o formato de um arquivo, sem extrair metadados. */
  public IdentificationResult identify(Path file) {
    return identify(open(file));
  }

  /** Identifica o formato de uma fonte, sem extrair metadados. */
  public IdentificationResult identify(InputSource source) {
    checkSize(source);
    DetectionResolver.Outcome outcome = resolver.resolve(detectionRequest(source));
    return new IdentificationResult(fileInfo(source), outcome.result(), outcome.warnings());
  }

  private MetadataResult run(InputSource source) {
    long start = System.nanoTime();
    DetectionResolver.Outcome outcome = resolver.resolve(detectionRequest(source));
    List<Warning> warnings = new ArrayList<>(outcome.warnings());

    if (outcome.result().isEmpty()) {
      return result(
          source, outcome.result(), ExtractionStatus.UNKNOWN_FORMAT, List.of(), warnings, start);
    }
    DetectionResult detection = outcome.result().get();
    FileFormat format = detection.format();

    Optional<MetadataParser> found = parsers.find(format.id());
    if (found.isEmpty()) {
      warnings.add(
          Warning.of(
              WarningCode.NO_PARSER,
              "No metadata parser is registered for format '" + format.id() + "'"));
      return result(
          source, outcome.result(), ExtractionStatus.UNSUPPORTED, List.of(), warnings, start);
    }
    MetadataParser parser = found.get();

    DefaultParseContext context = new DefaultParseContext(detection, limits, start);
    boolean failed = false;
    try {
      parser.parse(source, context);
    } catch (AnalysisCancelledException e) {
      context.warn(WarningCode.CANCELLED, e.getMessage());
    } catch (InputLimitExceededException e) {
      throw e; // stream acima de maxFileSize: a entrada é recusada, como um arquivo grande demais
    } catch (IOException | RuntimeException | StackOverflowError e) {
      LOG.debug("Parser '{}' failed", parser.id(), e);
      failed = true;
      context.warn(
          WarningCode.PARSER_ERROR, "Parser '" + parser.id() + "' failed: " + Failures.describe(e));
    }

    PrimarySelector.Selection selection = PrimarySelector.select(context.entries(), parser.id());
    warnings.addAll(context.warnings());
    warnings.addAll(selection.warnings());

    ExtractionStatus status;
    if (failed) {
      status = ExtractionStatus.FAILED;
    } else if (warnings.stream().anyMatch(w -> w.code().dataLoss())) {
      status = ExtractionStatus.PARTIAL;
    } else {
      status = ExtractionStatus.SUCCESS;
    }
    return result(source, outcome.result(), status, selection.metadata(), warnings, start);
  }

  /** O tamanho de streams só é conhecido depois da análise, por isso o FileInfo sai aqui. */
  private static MetadataResult result(
      InputSource source,
      Optional<DetectionResult> detection,
      ExtractionStatus status,
      List<Metadata> metadata,
      List<Warning> warnings,
      long start) {
    Duration duration = Duration.ofNanos(System.nanoTime() - start);
    return new MetadataResult(fileInfo(source), detection, status, metadata, warnings, duration);
  }

  private DetectionRequest detectionRequest(InputSource source) {
    try (InputStream in = source.openStream()) {
      return DetectionRequest.of(in.readNBytes(limits.maxDetectionBytes()), source.fileName());
    } catch (IOException e) {
      throw new InvalidInputException("Could not read input", e);
    }
  }

  private InputSource open(Path file) {
    Objects.requireNonNull(file, "file");
    if (!Files.exists(file)) {
      throw new InvalidInputException("File not found: " + file);
    }
    if (!Files.isRegularFile(file)) {
      throw new InvalidInputException("Not a regular file: " + file);
    }
    if (!Files.isReadable(file)) {
      throw new InvalidInputException("File is not readable: " + file);
    }
    InputSource source;
    try {
      source = new PathInputSource(file);
    } catch (IOException e) {
      throw new InvalidInputException("Could not open file: " + file, e);
    }
    checkSize(source);
    return source;
  }

  private void checkSize(InputSource source) {
    OptionalLong size = source.size();
    if (size.isPresent() && size.getAsLong() > limits.maxFileSize()) {
      throw new InputLimitExceededException(
          "Input of "
              + size.getAsLong()
              + " bytes exceeds maxFileSize of "
              + limits.maxFileSize()
              + " bytes");
    }
  }

  private static FileInfo fileInfo(InputSource source) {
    return fileInfo(source.fileName(), source.size());
  }

  private static FileInfo fileInfo(Optional<String> name, OptionalLong size) {
    return new FileInfo(name, size, name.flatMap(FileNames::extension));
  }

  /** Construtor de {@link MetadataAnalyzer}. */
  public static final class Builder {

    private AnalysisLimits limits = AnalysisLimits.defaults();
    private boolean discoverProviders = true;
    private final List<MetadataProvider> providers = new ArrayList<>();

    private Builder() {}

    public Builder limits(AnalysisLimits newLimits) {
      this.limits = Objects.requireNonNull(newLimits, "limits");
      return this;
    }

    /** Liga ou desliga a descoberta de provedores por {@link ServiceLoader}. Ligada por padrão. */
    public Builder discoverProviders(boolean discover) {
      this.discoverProviders = discover;
      return this;
    }

    /** Registra um provedor explicitamente, além dos descobertos. */
    public Builder addProvider(MetadataProvider provider) {
      providers.add(Objects.requireNonNull(provider, "provider"));
      return this;
    }

    /**
     * Cria o analisador.
     *
     * @throws IllegalStateException se os provedores forem inconsistentes: formatos conflitantes,
     *     ids repetidos ou parser de formato inexistente
     */
    public MetadataAnalyzer build() {
      List<MetadataProvider> all = new ArrayList<>(providers);
      if (discoverProviders) {
        Set<Class<?>> explicit = new HashSet<>();
        providers.forEach(p -> explicit.add(p.getClass()));
        ServiceLoader.load(MetadataProvider.class).stream()
            .filter(p -> !explicit.contains(p.type()))
            .sorted(Comparator.comparing(p -> p.type().getName()))
            .map(ServiceLoader.Provider::get)
            .forEach(all::add);
      }

      List<FileFormat> declaredFormats = new ArrayList<>();
      List<FormatDetector> detectors = new ArrayList<>();
      List<MetadataParser> parsers = new ArrayList<>();
      for (MetadataProvider provider : all) {
        declaredFormats.addAll(provider.formats());
        detectors.addAll(provider.detectors());
        parsers.addAll(provider.parsers());
      }
      requireUniqueIds(detectors.stream().map(FormatDetector::id).toList(), "detector");
      requireUniqueIds(parsers.stream().map(MetadataParser::id).toList(), "parser");
      try {
        FormatRegistry formats = FormatRegistry.of(declaredFormats);
        return new MetadataAnalyzer(limits, formats, detectors, parsers);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("Inconsistent providers: " + e.getMessage(), e);
      }
    }

    private static void requireUniqueIds(List<String> ids, String kind) {
      Set<String> seen = new HashSet<>();
      for (String id : ids) {
        if (!seen.add(id)) {
          throw new IllegalStateException("Duplicate " + kind + " id '" + id + "'");
        }
      }
    }
  }
}
