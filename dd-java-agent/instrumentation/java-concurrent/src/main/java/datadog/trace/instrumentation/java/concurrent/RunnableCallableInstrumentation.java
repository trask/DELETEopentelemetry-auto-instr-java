package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument {@link Runnable} and {@Callable} */
@Slf4j
@AutoService(Instrumenter.class)
public final class RunnableCallableInstrumentation extends Instrumenter.Default {

  public RunnableCallableInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named(Runnable.class.getName()).or(named(Callable.class.getName()))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AdviceUtils.class.getName(),
    };
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put(Runnable.class.getName(), Span.class.getName());
    map.put(Callable.class.getName(), Span.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("run").and(takesArguments(0)).and(isPublic()), RunnableAdvice.class.getName());
    transformers.put(
        named("call").and(takesArguments(0)).and(isPublic()), CallableAdvice.class.getName());
    return transformers;
  }

  public static class RunnableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.This final Runnable thiz) {
      Span span = InstrumentationContext.get(Runnable.class, Span.class).get(thiz);
      return span == null ? null : GlobalTracer.get().withSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  public static class CallableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.This final Callable thiz) {
      Span span = InstrumentationContext.get(Callable.class, Span.class).get(thiz);
      return span == null ? null : GlobalTracer.get().withSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
