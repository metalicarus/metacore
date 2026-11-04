package br.com.genosha.metacore.isolation;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.ExtractionStatus;
import br.com.genosha.metacore.api.FileInfo;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import br.com.genosha.metacore.isolation.internal.Protocol;
import br.com.genosha.metacore.isolation.internal.WorkerMain;
import br.com.genosha.metacore.isolation.internal.WorkerProcess;
import br.com.genosha.metacore.metadata.Warning;
import br.com.genosha.metacore.metadata.WarningCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Analisa em processos Java separados (workers), com prazo rígido e teto de memória (ADR 0016). O
 * resultado é o mesmo de {@link br.com.genosha.metacore.api.MetadataAnalyzer}; o que muda é o que
 * acontece quando um parser trava ou derruba a JVM:
 *
 * <ul>
 *   <li>passou de {@code timeout}: o worker é morto e o resultado é {@code FAILED} com aviso {@link
 *       IsolationWarnings#TIMEOUT};
 *   <li>o worker morreu (falta de memória, queda da JVM): {@code FAILED} com aviso {@link
 *       IsolationWarnings#WORKER_FAILED}.
 * </ul>
 *
 * <p>Thread-safe. Os workers são reaproveitados entre análises e criados sob demanda até o limite
 * de {@code workers}; quem chama além disso espera um worker livre. Feche a instância para encerrar
 * os processos.
 *
 * <p>O worker descobre os provedores por {@code ServiceLoader} no próprio classpath. Provedores
 * registrados em código não atravessam o processo.
 */
public final class IsolatedAnalyzer implements AutoCloseable {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

  private final AnalysisLimits limits;
  private final Duration timeout;
  private final int maxAnalysesPerWorker;
  private final List<String> command;
  private final Semaphore permits;
  private final Deque<WorkerProcess> idle = new ConcurrentLinkedDeque<>();
  private final ScheduledThreadPoolExecutor watchdog;
  private volatile boolean closed;

  private IsolatedAnalyzer(Builder builder) {
    this.limits = builder.limits;
    this.timeout = builder.timeout;
    this.maxAnalysesPerWorker = builder.maxAnalysesPerWorker;
    this.command = List.copyOf(builder.command());
    this.permits = new Semaphore(builder.workers, true);
    this.watchdog =
        new ScheduledThreadPoolExecutor(
            1,
            task -> {
              Thread thread = new Thread(task, "metacore-isolation-watchdog");
              thread.setDaemon(true);
              return thread;
            });
    this.watchdog.setRemoveOnCancelPolicy(true);
  }

  /** Analisador com os valores padrão do {@link Builder}. */
  public static IsolatedAnalyzer createDefault() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Limites aplicados a cada análise, dentro do worker. */
  public AnalysisLimits limits() {
    return limits;
  }

  /** Prazo rígido de cada análise. */
  public Duration timeout() {
    return timeout;
  }

  /** Comando que sobe cada worker. */
  public List<String> command() {
    return command;
  }

  /**
   * Identifica e extrai os metadados de um arquivo, aberto pelo próprio worker.
   *
   * @throws InvalidInputException se o arquivo não existir ou não puder ser lido
   * @throws InputLimitExceededException se passar de {@code maxFileSize}
   * @throws IsolationException se nenhum worker puder ser usado
   */
  public MetadataResult analyze(Path file) {
    Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath();
    return execute(() -> fileInfo(absolute), worker -> worker.analyze(absolute, timeout));
  }

  /**
   * Identifica e extrai os metadados de um stream. O worker pede só os blocos que o parser lê (ADR
   * 0015). O stream não é fechado.
   *
   * @param fileName nome declarado do conteúdo, usado só como evidência de extensão; pode ser nulo
   * @throws IsolationException se nenhum worker puder ser usado
   */
  public MetadataResult analyze(InputStream in, String fileName) {
    Objects.requireNonNull(in, "in");
    Optional<String> name = Optional.ofNullable(fileName);
    return execute(
        () -> FileInfo.of(name, OptionalLong.empty()), worker -> worker.analyze(in, name, timeout));
  }

  /**
   * Encerra os workers ociosos e não aceita novas análises. Análises em andamento terminam
   * normalmente e seus workers são encerrados em seguida.
   */
  @Override
  public void close() {
    closed = true;
    drainIdle();
    watchdog.shutdown();
  }

  private MetadataResult execute(
      Supplier<FileInfo> fileInfo, Function<WorkerProcess, WorkerProcess.Outcome> analysis) {
    if (closed) {
      throw new IllegalStateException("IsolatedAnalyzer is closed");
    }
    try {
      permits.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IsolationException("Interrupted while waiting for a worker", e);
    }
    WorkerProcess worker = null;
    try {
      worker = checkout();
      long start = System.nanoTime();
      WorkerProcess.Outcome outcome = analysis.apply(worker);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
      return switch (outcome) {
        case WorkerProcess.Completed completed -> completed.result();
        case WorkerProcess.Rejected rejected -> throw rejection(rejected);
        case WorkerProcess.TimedOut timedOut ->
            failed(
                fileInfo.get(),
                IsolationWarnings.TIMEOUT,
                "Analysis did not finish within "
                    + timeout.toMillis()
                    + " ms; the worker process was terminated",
                elapsed);
        case WorkerProcess.Failed failed ->
            failed(fileInfo.get(), IsolationWarnings.WORKER_FAILED, failed.message(), elapsed);
      };
    } finally {
      if (worker != null) {
        checkin(worker);
      }
      permits.release();
    }
  }

  private WorkerProcess checkout() {
    WorkerProcess worker;
    while ((worker = idle.pollFirst()) != null) {
      if (worker.reusable(maxAnalysesPerWorker)) {
        return worker;
      }
      worker.destroy();
    }
    return WorkerProcess.start(command, limits, STARTUP_TIMEOUT, watchdog);
  }

  private void checkin(WorkerProcess worker) {
    if (closed || !worker.reusable(maxAnalysesPerWorker)) {
      worker.destroy();
      return;
    }
    idle.offerFirst(worker);
    if (closed) {
      drainIdle(); // close() pode ter rodado entre a verificação e o offerFirst
    }
  }

  private void drainIdle() {
    WorkerProcess worker;
    while ((worker = idle.pollFirst()) != null) {
      worker.destroy();
    }
  }

  private static RuntimeException rejection(WorkerProcess.Rejected rejected) {
    return rejected.kind() == Protocol.LIMIT_EXCEEDED
        ? new InputLimitExceededException(rejected.message())
        : new InvalidInputException(rejected.message());
  }

  private static MetadataResult failed(
      FileInfo file, WarningCode code, String message, Duration duration) {
    return new MetadataResult(
        file,
        Optional.empty(),
        ExtractionStatus.FAILED,
        List.of(),
        List.of(Warning.of(code, message)),
        duration);
  }

  private static FileInfo fileInfo(Path file) {
    Optional<String> name = Optional.ofNullable(file.getFileName()).map(Path::toString);
    OptionalLong size;
    try {
      size = OptionalLong.of(Files.size(file));
    } catch (IOException e) {
      size = OptionalLong.empty();
    }
    return FileInfo.of(name, size);
  }

  /** Construtor de {@link IsolatedAnalyzer}. */
  public static final class Builder {

    private AnalysisLimits limits = AnalysisLimits.defaults();
    private Duration timeout = Duration.ofSeconds(30);
    private long maxHeapSize = 512L * 1024 * 1024;
    private int workers = 1;
    private int maxAnalysesPerWorker = 1000;
    private Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
    private String classPath = System.getProperty("java.class.path");
    private final List<String> jvmOptions = new ArrayList<>();
    private final List<String> launcher = new ArrayList<>();

    private Builder() {}

    /** Limites da análise, aplicados dentro do worker. Padrão: {@link AnalysisLimits#defaults}. */
    public Builder limits(AnalysisLimits newLimits) {
      this.limits = Objects.requireNonNull(newLimits, "limits");
      return this;
    }

    /**
     * Prazo rígido de cada análise, contado do envio do pedido ao worker. Padrão: 30 s. Combine com
     * {@code maxAnalysisTime} menor para ter, antes, um resultado {@code PARTIAL} pelo cancelamento
     * cooperativo.
     */
    public Builder timeout(Duration newTimeout) {
      Objects.requireNonNull(newTimeout, "timeout");
      if (newTimeout.isNegative() || newTimeout.isZero()) {
        throw new IllegalArgumentException("timeout must be positive");
      }
      this.timeout = newTimeout;
      return this;
    }

    /** Heap máximo de cada worker ({@code -Xmx}), em bytes. Padrão: 512 MiB. */
    public Builder maxHeapSize(long bytes) {
      if (bytes < 16L * 1024 * 1024) {
        throw new IllegalArgumentException("maxHeapSize must be at least 16 MiB");
      }
      this.maxHeapSize = bytes;
      return this;
    }

    /** Quantos workers podem existir ao mesmo tempo, isto é, análises em paralelo. Padrão: 1. */
    public Builder workers(int count) {
      if (count < 1) {
        throw new IllegalArgumentException("workers must be at least 1");
      }
      this.workers = count;
      return this;
    }

    /**
     * Quantas análises um worker atende antes de ser trocado por um novo, o que limita o efeito de
     * vazamentos de memória nas bibliotecas de formato. Padrão: 1000.
     */
    public Builder maxAnalysesPerWorker(int count) {
      if (count < 1) {
        throw new IllegalArgumentException("maxAnalysesPerWorker must be at least 1");
      }
      this.maxAnalysesPerWorker = count;
      return this;
    }

    /** Executável {@code java} do worker. Padrão: o da JVM atual. */
    public Builder javaExecutable(Path executable) {
      this.javaExecutable = Objects.requireNonNull(executable, "executable");
      return this;
    }

    /**
     * Classpath do worker, com o MetaCore e os módulos de formato. Padrão: {@code java.class.path}
     * da JVM atual, que não serve quando as classes não vêm de arquivos no classpath (Spring Boot,
     * servidores de aplicação).
     */
    public Builder classPath(String newClassPath) {
      this.classPath = Objects.requireNonNull(newClassPath, "classPath");
      return this;
    }

    /** Opções extras da JVM do worker, como {@code -Djava.io.tmpdir=...}. */
    public Builder addJvmOption(String option) {
      jvmOptions.add(Objects.requireNonNull(option, "option"));
      return this;
    }

    /**
     * Prefixo do comando, como {@code nice -n 10} ou um wrapper de sandbox que executa o resto da
     * linha de comando.
     */
    public Builder launcher(List<String> prefix) {
      launcher.clear();
      launcher.addAll(List.copyOf(prefix));
      return this;
    }

    public IsolatedAnalyzer build() {
      if (classPath == null || classPath.isBlank()) {
        throw new IllegalStateException("No class path for the worker; set it with classPath()");
      }
      return new IsolatedAnalyzer(this);
    }

    private List<String> command() {
      List<String> command = new ArrayList<>(launcher);
      command.add(javaExecutable.toString());
      command.add("-Xmx" + maxHeapSize);
      command.add("-XX:+ExitOnOutOfMemoryError");
      // O stdout é do protocolo; mensagens da própria JVM, como a de falta de memória, vão para o
      // stderr.
      command.add("-XX:+DisplayVMOutputToStderr");
      command.add("-XX:+UseSerialGC");
      command.add("-Djava.awt.headless=true");
      command.addAll(jvmOptions);
      command.add("-cp");
      command.add(classPath);
      command.add(WorkerMain.class.getName());
      return command;
    }
  }
}
