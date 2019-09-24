package datadog.trace.instrumentation.java.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.java.concurrent.CallableWrapper;
import datadog.trace.bootstrap.instrumentation.java.concurrent.RunnableWrapper;
import io.opentelemetry.trace.Span;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JavaExecutorInstrumentation extends AbstractExecutorInstrumentation {

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put(Runnable.class.getName(), Span.class.getName());
    map.put(Callable.class.getName(), Span.class.getName());
    map.put(ForkJoinTask.class.getName(), Span.class.getName());
    map.put(Future.class.getName(), Span.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("execute").and(takesArgument(0, Runnable.class)),
        SetExecuteRunnableStateAdvice.class.getName());
    transformers.put(
        named("execute").and(takesArgument(0, ForkJoinTask.class)),
        SetJavaForkJoinStateAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, Runnable.class)),
        SetSubmitRunnableStateAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, Callable.class)),
        SetCallableStateAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, ForkJoinTask.class)),
        SetJavaForkJoinStateAdvice.class.getName());
    transformers.put(
        nameMatches("invoke(Any|All)$").and(takesArgument(0, Collection.class)),
        SetCallableStateForCallableCollectionAdvice.class.getName());
    transformers.put(
        nameMatches("invoke").and(takesArgument(0, ForkJoinTask.class)),
        SetJavaForkJoinStateAdvice.class.getName());
    transformers.put(
        named("schedule").and(takesArgument(0, Runnable.class)),
        SetSubmitRunnableStateAdvice.class.getName());
    transformers.put(
        named("schedule").and(takesArgument(0, Callable.class)),
        SetCallableStateAdvice.class.getName());
    return transformers;
  }

  public static class SetExecuteRunnableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      final Span span = GlobalTracer.get().getCurrentSpan();
      final Runnable newTask = RunnableWrapper.wrapIfNeeded(task);
      // It is important to check potentially wrapped task if we can instrument task in this
      // executor. Some executors do not support wrapped tasks.
      if (ExecutorInstrumentationUtils.shouldAttachSpanToTask(span, newTask, executor)) {
        task = newTask;
        final ContextStore<Runnable, Span> contextStore =
            InstrumentationContext.get(Runnable.class, Span.class);
        ExecutorInstrumentationUtils.setupState(contextStore, newTask, span);
      }
    }
  }

  public static class SetJavaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0, readOnly = false) final ForkJoinTask task) {
      final Span span = GlobalTracer.get().getCurrentSpan();
      if (ExecutorInstrumentationUtils.shouldAttachSpanToTask(span, task, executor)) {
        final ContextStore<ForkJoinTask, Span> contextStore =
            InstrumentationContext.get(ForkJoinTask.class, Span.class);
        ExecutorInstrumentationUtils.setupState(contextStore, task, span);
      }
    }
  }

  public static class SetSubmitRunnableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      final Span span = GlobalTracer.get().getCurrentSpan();
      final Runnable newTask = RunnableWrapper.wrapIfNeeded(task);
      // It is important to check potentially wrapped task if we can instrument task in this
      // executor. Some executors do not support wrapped tasks.
      if (ExecutorInstrumentationUtils.shouldAttachSpanToTask(span, newTask, executor)) {
        task = newTask;
        final ContextStore<Runnable, Span> contextStore =
            InstrumentationContext.get(Runnable.class, Span.class);
        ExecutorInstrumentationUtils.setupState(contextStore, newTask, span);
      }
    }
  }

  public static class SetCallableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0, readOnly = false) Callable task) {
      final Span span = GlobalTracer.get().getCurrentSpan();
      final Callable newTask = CallableWrapper.wrapIfNeeded(task);
      // It is important to check potentially wrapped task if we can instrument task in this
      // executor. Some executors do not support wrapped tasks.
      if (ExecutorInstrumentationUtils.shouldAttachSpanToTask(span, newTask, executor)) {
        task = newTask;
        final ContextStore<Callable, Span> contextStore =
            InstrumentationContext.get(Callable.class, Span.class);
        ExecutorInstrumentationUtils.setupState(contextStore, newTask, span);
      }
    }
  }

  public static class SetCallableStateForCallableCollectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void submitEnter(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0, readOnly = false) Collection<? extends Callable<?>> tasks) {
      final Span span = GlobalTracer.get().getCurrentSpan();
      if (span.getContext().isValid() && tasks != null) {
        final Collection<Callable<?>> wrappedTasks = new ArrayList<>(tasks.size());
        for (final Callable<?> task : tasks) {
          if (task != null) {
            final Callable newTask = CallableWrapper.wrapIfNeeded(task);
            if (ExecutorInstrumentationUtils.isExecutorDisabledForThisTask(executor, newTask)) {
              wrappedTasks.add(task);
            } else {
              wrappedTasks.add(newTask);
              final ContextStore<Callable, Span> contextStore =
                  InstrumentationContext.get(Callable.class, Span.class);
              ExecutorInstrumentationUtils.setupState(contextStore, newTask, span);
            }
          }
        }
        tasks = wrappedTasks;
      }
    }
  }
}
