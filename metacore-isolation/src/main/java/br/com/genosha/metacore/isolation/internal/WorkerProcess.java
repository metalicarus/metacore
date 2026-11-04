package br.com.genosha.metacore.isolation.internal;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.isolation.IsolationException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Um processo worker visto por quem chama. Atende uma análise por vez; não é thread-safe.
 *
 * <p>Cada troca de mensagens tem um prazo: quando ele passa, o processo é morto, a leitura
 * bloqueada termina com fim de stream e o worker fica inutilizável.
 */
public final class WorkerProcess {

  /** Desfecho de uma análise. */
  public sealed interface Outcome {}

  /** O worker respondeu com um resultado. */
  public record Completed(MetadataResult result) implements Outcome {}

  /** O worker recusou a entrada, como a análise em processo faria com uma exceção. */
  public record Rejected(int kind, String message) implements Outcome {}

  /** O prazo passou e o worker foi morto. */
  public record TimedOut() implements Outcome {}

  /** O worker morreu ou saiu do protocolo. */
  public record Failed(String message) implements Outcome {}

  private static final int STDERR_TAIL_BYTES = 4096;
  private static final int MAX_DETAIL_LENGTH = 300;

  private final Process process;
  private final DataInputStream in;
  private final DataOutputStream out;
  private final AnalysisLimits limits;
  private final ScheduledExecutorService watchdog;
  private final StderrTail stderr;
  private final Object lock = new Object();
  private boolean running;
  private boolean expired;
  private boolean broken;
  private int analyses;

  private WorkerProcess(Process process, AnalysisLimits limits, ScheduledExecutorService watchdog) {
    this.process = process;
    this.in = new DataInputStream(new BufferedInputStream(process.getInputStream()));
    this.out = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
    this.limits = limits;
    this.watchdog = watchdog;
    this.stderr = new StderrTail(process.getErrorStream());
  }

  /**
   * Sobe um worker e espera ele ficar pronto.
   *
   * @throws IsolationException se o processo não subir, não responder dentro de {@code
   *     startupTimeout} ou não falar o protocolo
   */
  public static WorkerProcess start(
      List<String> command,
      AnalysisLimits limits,
      Duration startupTimeout,
      ScheduledExecutorService watchdog) {
    Process process;
    try {
      process = new ProcessBuilder(command).start();
    } catch (IOException e) {
      throw new IsolationException("Could not start worker process '" + command.get(0) + "'", e);
    }
    WorkerProcess worker = new WorkerProcess(process, limits, watchdog);
    worker.handshake(startupTimeout);
    return worker;
  }

  private void handshake(Duration startupTimeout) {
    ScheduledFuture<?> deadline = arm(startupTimeout);
    try {
      int magic = in.readInt();
      int version = in.readInt();
      if (magic != Protocol.MAGIC || version != Protocol.VERSION) {
        throw new ProtocolException("Unexpected greeting from worker process");
      }
      Protocol.writeLimits(out, limits);
      out.flush();
      int status = in.readUnsignedByte();
      if (status == Protocol.STARTUP_ERROR) {
        String message = Protocol.readMessage(in);
        disarm(deadline);
        destroy();
        throw new IsolationException("Worker process could not start: " + message);
      }
      if (status != Protocol.READY) {
        throw new ProtocolException("Unexpected startup status " + status);
      }
    } catch (IOException e) {
      String reason =
          disarm(deadline)
              ? "did not start within " + startupTimeout.toMillis() + " ms"
              : describeFailure(e);
      destroy();
      throw new IsolationException("Worker process " + reason, e);
    }
    disarm(deadline);
  }

  /** Analisa um arquivo que o worker abre sozinho. */
  public Outcome analyze(Path file, Duration timeout) {
    return exchange(
        timeout,
        () -> {
          out.writeByte(Protocol.ANALYZE_PATH);
          Protocol.writeString(out, file.toString());
          out.flush();
        },
        null);
  }

  /** Analisa um stream, mandando ao worker cada bloco que ele pedir. */
  public Outcome analyze(InputStream source, Optional<String> fileName, Duration timeout) {
    return exchange(
        timeout,
        () -> {
          out.writeByte(Protocol.ANALYZE_STREAM);
          Protocol.writeOptionalString(out, fileName);
          out.flush();
        },
        source);
  }

  /** Indica se o worker pode atender outra análise. */
  public boolean reusable(int maxAnalyses) {
    return !broken && analyses < maxAnalyses && process.isAlive();
  }

  /** Fecha o stdin, o que encerra o worker, e garante que ele morra. */
  public void destroy() {
    broken = true;
    try {
      out.close();
    } catch (IOException e) {
      // o processo já se foi
    }
    process.destroyForcibly();
  }

  private interface Request {
    void send() throws IOException;
  }

  private Outcome exchange(Duration timeout, Request request, InputStream source) {
    analyses++;
    ScheduledFuture<?> deadline = arm(timeout);
    Outcome outcome;
    try {
      request.send();
      outcome = receive(source);
    } catch (IOException e) {
      outcome =
          disarm(deadline) ? new TimedOut() : new Failed("Worker process " + describeFailure(e));
      broken = true;
      return outcome;
    }
    if (disarm(deadline)) {
      broken = true; // a resposta chegou, mas o processo foi morto logo depois
    }
    if (outcome instanceof Rejected rejected && rejected.kind() == Protocol.INTERNAL) {
      broken = true;
      return new Failed("Worker process failed: " + rejected.message());
    }
    return outcome;
  }

  private Outcome receive(InputStream source) throws IOException {
    byte[] chunk = null;
    boolean sourceDone = false;
    while (true) {
      int type = in.readUnsignedByte();
      switch (type) {
        case Protocol.RESULT -> {
          return new Completed(Protocol.readResult(in, limits));
        }
        case Protocol.ERROR -> {
          int kind = in.readUnsignedByte();
          return new Rejected(kind, Protocol.readMessage(in));
        }
        case Protocol.READ -> {
          int max = in.readInt();
          if (source == null || max <= 0 || max > Protocol.CHUNK) {
            throw new ProtocolException("Unexpected read request");
          }
          if (chunk == null) {
            chunk = new byte[Protocol.CHUNK];
          }
          if (sourceDone) {
            out.writeInt(Protocol.EOF);
          } else {
            int count;
            try {
              do {
                count = source.read(chunk, 0, max);
              } while (count == 0);
            } catch (IOException | RuntimeException e) {
              sourceDone = true;
              out.writeInt(Protocol.SOURCE_ERROR);
              Protocol.writeString(out, String.valueOf(e.getMessage()));
              out.flush();
              continue;
            }
            if (count < 0) {
              sourceDone = true;
              out.writeInt(Protocol.EOF);
            } else {
              out.writeInt(count);
              out.write(chunk, 0, count);
            }
          }
          out.flush();
        }
        default -> throw new ProtocolException("Unexpected message " + type);
      }
    }
  }

  private ScheduledFuture<?> arm(Duration timeout) {
    synchronized (lock) {
      running = true;
      expired = false;
    }
    return watchdog.schedule(this::expire, timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  /** Encerra o prazo e diz se ele chegou a passar. */
  private boolean disarm(ScheduledFuture<?> deadline) {
    deadline.cancel(false);
    synchronized (lock) {
      running = false;
      return expired;
    }
  }

  private void expire() {
    synchronized (lock) {
      if (running) {
        expired = true;
        process.destroyForcibly();
      }
    }
  }

  /** Uma resposta inválida costuma ser o worker morrendo; quando ele sai, o código explica mais. */
  private String describeFailure(IOException failure) {
    boolean exited;
    try {
      exited = process.waitFor(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      exited = false;
    }
    if (!exited) {
      process.destroyForcibly();
      return (failure instanceof ProtocolException
              ? "sent an invalid response: "
              : "stopped responding: ")
          + failure.getMessage();
    }
    String message = "exited with code " + process.exitValue();
    return stderr.lastLine().map(line -> message + ": " + line).orElse(message);
  }

  /** Drena o stderr do worker, guardando só o final para mensagens de erro. */
  private static final class StderrTail {

    private final ByteArrayOutputStream tail = new ByteArrayOutputStream();
    private final Thread thread;

    StderrTail(InputStream stderr) {
      thread =
          Thread.ofPlatform()
              .daemon()
              .name("metacore-worker-stderr")
              .start(
                  () -> {
                    byte[] buffer = new byte[1024];
                    try (stderr) {
                      int count;
                      while ((count = stderr.read(buffer)) != -1) {
                        append(buffer, count);
                      }
                    } catch (IOException e) {
                      // processo encerrado
                    }
                  });
    }

    private synchronized void append(byte[] buffer, int count) {
      tail.write(buffer, 0, count);
      if (tail.size() > 2 * STDERR_TAIL_BYTES) {
        byte[] all = tail.toByteArray();
        tail.reset();
        tail.write(all, all.length - STDERR_TAIL_BYTES, STDERR_TAIL_BYTES);
      }
    }

    /** Última linha não vazia, depois que o processo terminou. */
    Optional<String> lastLine() {
      try {
        thread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      String text;
      synchronized (this) {
        text = tail.toString(Charset.defaultCharset());
      }
      List<String> lines = text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
      if (lines.isEmpty()) {
        return Optional.empty();
      }
      String last = lines.get(lines.size() - 1);
      return Optional.of(
          last.length() > MAX_DETAIL_LENGTH ? last.substring(0, MAX_DETAIL_LENGTH) + "..." : last);
    }
  }
}
