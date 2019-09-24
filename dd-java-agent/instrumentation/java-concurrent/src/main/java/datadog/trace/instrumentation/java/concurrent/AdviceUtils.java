package datadog.trace.instrumentation.java.concurrent;

import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.bootstrap.ContextStore;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import lombok.extern.slf4j.Slf4j;

/** Helper utils for Runnable/Callable instrumentation */
@Slf4j
public class AdviceUtils {

  /**
   * Start scope for a given task
   *
   * @param contextStore context storage for task's state
   * @param task task to start scope for
   * @param <T> task's type
   * @return scope if scope was started, or null
   */
  public static <T> Scope startTaskScope(final ContextStore<T, Span> contextStore, final T task) {
    Span span = contextStore.get(task);
    if (span != null) {
      return GlobalTracer.get().withSpan(span);
    }
    return null;
  }

  public static void endTaskScope(final Scope scope) {
    if (scope != null) {
      scope.close();
    }
  }
}
