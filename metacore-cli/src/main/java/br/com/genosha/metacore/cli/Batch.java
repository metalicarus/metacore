package br.com.genosha.metacore.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Executa uma tarefa por entrada, em até {@code jobs} threads, e entrega os resultados na ordem das
 * entradas. No máximo {@code 2 * jobs} resultados ficam em memória esperando a vez.
 */
final class Batch {

  private Batch() {}

  static <I, R> void run(List<I> inputs, int jobs, Function<I, R> task, Consumer<R> sink) {
    if (jobs <= 1 || inputs.size() < 2) {
      inputs.forEach(input -> sink.accept(task.apply(input)));
      return;
    }
    ExecutorService pool =
        Executors.newFixedThreadPool(
            jobs, Thread.ofPlatform().daemon().name("metacore-analysis-", 1).factory());
    try {
      Deque<Future<R>> pending = new ArrayDeque<>();
      for (I input : inputs) {
        pending.add(pool.submit(() -> task.apply(input)));
        if (pending.size() >= 2 * jobs) {
          sink.accept(get(pending.removeFirst()));
        }
      }
      while (!pending.isEmpty()) {
        sink.accept(get(pending.removeFirst()));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private static <R> R get(Future<R> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(e.getCause());
    }
  }
}
