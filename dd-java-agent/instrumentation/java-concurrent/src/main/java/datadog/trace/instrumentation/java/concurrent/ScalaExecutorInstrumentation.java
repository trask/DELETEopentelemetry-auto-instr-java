package datadog.trace.instrumentation.java.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import io.opentelemetry.trace.Span;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.forkjoin.ForkJoinTask;

@Slf4j
@AutoService(Instrumenter.class)
public final class ScalaExecutorInstrumentation extends AbstractExecutorInstrumentation {

  public ScalaExecutorInstrumentation() {
    super(EXEC_NAME + ".scala_fork_join");
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME, Span.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("execute")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        SetScalaForkJoinStateAdvice.class.getName());
    transformers.put(
        named("submit")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        SetScalaForkJoinStateAdvice.class.getName());
    transformers.put(
        nameMatches("invoke")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        SetScalaForkJoinStateAdvice.class.getName());
    return transformers;
  }

  public static class SetScalaForkJoinStateAdvice {

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
}
