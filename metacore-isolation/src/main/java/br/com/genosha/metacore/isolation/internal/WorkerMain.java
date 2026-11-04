package br.com.genosha.metacore.isolation.internal;

import br.com.genosha.metacore.api.AnalysisLimits;
import br.com.genosha.metacore.api.MetadataAnalyzer;
import br.com.genosha.metacore.api.MetadataResult;
import br.com.genosha.metacore.exception.InputLimitExceededException;
import br.com.genosha.metacore.exception.InvalidInputException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Ponto de entrada do worker. Atende uma análise por vez até o stdin fechar.
 *
 * <p>O stdout pertence ao protocolo: antes de carregar qualquer parser, {@code System.out} passa a
 * escrever no stderr, para que um {@code println} perdido não corrompa as mensagens.
 */
public final class WorkerMain {

  private WorkerMain() {}

  public static void main(String[] args) {
    DataInputStream in =
        new DataInputStream(new BufferedInputStream(new FileInputStream(FileDescriptor.in)));
    DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)));
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true));
    System.setIn(InputStream.nullInputStream());
    exitWithParent();
    int code;
    try {
      code = serve(in, out);
    } catch (IOException e) {
      code = 1; // o pai sumiu ou mandou algo fora do protocolo
    }
    System.exit(code);
  }

  /** Um worker que perdeu o pai não tem para quem responder. */
  private static void exitWithParent() {
    ProcessHandle.current()
        .parent()
        .ifPresent(parent -> parent.onExit().thenRun(() -> Runtime.getRuntime().halt(1)));
  }

  private static int serve(DataInputStream in, DataOutputStream out) throws IOException {
    out.writeInt(Protocol.MAGIC);
    out.writeInt(Protocol.VERSION);
    out.flush();

    AnalysisLimits limits = Protocol.readLimits(in);
    MetadataAnalyzer analyzer;
    try {
      analyzer = MetadataAnalyzer.builder().limits(limits).build();
    } catch (RuntimeException | LinkageError e) {
      out.writeByte(Protocol.STARTUP_ERROR);
      Protocol.writeString(out, describe(e));
      out.flush();
      return 1;
    }
    out.writeByte(Protocol.READY);
    out.flush();

    while (true) {
      int request = in.read();
      if (request == -1) {
        return 0;
      }
      MetadataResult result;
      try {
        result =
            switch (request) {
              case Protocol.ANALYZE_PATH -> analyzer.analyze(Path.of(Protocol.readMessage(in)));
              case Protocol.ANALYZE_STREAM -> {
                Optional<String> name =
                    in.readBoolean() ? Optional.of(Protocol.readMessage(in)) : Optional.empty();
                yield analyzer.analyze(new RemoteInputStream(in, out), name.orElse(null));
              }
              default -> throw new ProtocolException("Unknown request " + request);
            };
      } catch (InputLimitExceededException e) {
        error(out, Protocol.LIMIT_EXCEEDED, e.getMessage());
        continue;
      } catch (InvalidInputException e) {
        error(out, Protocol.INVALID_INPUT, e.getMessage());
        continue;
      } catch (IOException e) {
        throw e;
      } catch (RuntimeException | Error e) {
        // Fora do que o core trata (ADR 0006): o estado do worker não é mais confiável.
        error(out, Protocol.INTERNAL, describe(e));
        return 1;
      }
      out.writeByte(Protocol.RESULT);
      Protocol.writeResult(out, result);
      out.flush();
    }
  }

  static String describe(Throwable failure) {
    String message = failure.getMessage();
    String type = failure.getClass().getSimpleName();
    return message == null || message.isBlank() ? type : type + ": " + message;
  }

  private static void error(DataOutputStream out, int kind, String message) throws IOException {
    out.writeByte(Protocol.ERROR);
    out.writeByte(kind);
    Protocol.writeString(out, message == null ? "" : message);
    out.flush();
  }
}
