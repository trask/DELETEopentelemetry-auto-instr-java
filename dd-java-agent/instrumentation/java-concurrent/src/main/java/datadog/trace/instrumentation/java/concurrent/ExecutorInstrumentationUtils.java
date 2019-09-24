package datadog.trace.instrumentation.java.concurrent;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.instrumentation.java.concurrent.CallableWrapper;
import datadog.trace.bootstrap.instrumentation.java.concurrent.RunnableWrapper;
import io.opentelemetry.trace.Span;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/** Utils for concurrent instrumentations. */
@Slf4j
public class ExecutorInstrumentationUtils {

  private static final WeakMap<Executor, Boolean> EXECUTORS_DISABLED_FOR_WRAPPED_TASKS =
      WeakMap.Provider.newWeakMap();

  /**
   * Checks if given task should get state attached.
   *
   * @param task task object
   * @param executor executor this task was scheduled on
   * @return true iff given task object should be wrapped
   */
  public static boolean shouldAttachSpanToTask(
      final Span span, final Object task, final Executor executor) {
    return span.getContext().isValid()
        && task != null
        && !ExecutorInstrumentationUtils.isExecutorDisabledForThisTask(executor, task);
  }

  /**
   * Create task state given current scope.
   *
   * @param contextStore context storage
   * @param task task instance
   * @param span current span
   * @param <T> task class type
   * @return new state
   */
  public static <T> void setupState(
      final ContextStore<T, Span> contextStore, final T task, final Span span) {
    contextStore.putIfAbsent(task, span);
  }

  public static void disableExecutorForWrappedTasks(final Executor executor) {
    log.debug("Disabling Executor tracing for wrapped tasks for instance {}", executor);
    EXECUTORS_DISABLED_FOR_WRAPPED_TASKS.put(executor, true);
  }

  /**
   * Check if Executor can accept given task.
   *
   * <p>Disabled executors cannot accept wrapped tasks, non wrapped tasks (i.e. tasks with injected
   * fields) should still work fine.
   */
  public static boolean isExecutorDisabledForThisTask(final Executor executor, final Object task) {
    return (task instanceof RunnableWrapper || task instanceof CallableWrapper)
        && EXECUTORS_DISABLED_FOR_WRAPPED_TASKS.containsKey(executor);
  }
}
