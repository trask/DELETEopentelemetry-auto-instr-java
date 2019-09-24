package datadog.trace.agent.test.base;

import datadog.trace.agent.tooling.AttributeNames;
import datadog.trace.agent.tooling.GlobalTracer;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import net.bytebuddy.asm.Advice;

public abstract class HttpServerTestAdvice {

  /**
   * This advice should be applied at the root of a http server request to validate the
   * instrumentation correctly ignores other traces.
   */
  public static class ServerEntryAdvice {
    @Advice.OnMethodEnter
    public static Span methodEnter() {
      if (!HttpServerTest.ENABLE_TEST_ADVICE.get()) {
        // Skip if not running the HttpServerTest.
        return null;
      }
      final Tracer tracer = GlobalTracer.get();
      // TODO trask: is there a better way to perfom this check?
      if (tracer.getCurrentSpan() != DefaultSpan.getInvalid()) {
        return DefaultSpan.getInvalid();
      } else {
        final Span span = tracer.spanBuilder("TEST_SPAN").startSpan();
        // TODO trask: should resource name be mapped to proto span.resource.labelsMap.name ?
        span.setAttribute(AttributeNames.RESOURCE_NAME, "ServerEntry");
        // TODO trask
        // ((TraceScope) span).setAsyncPropagation(true);
        return span;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void methodExit(@Advice.Enter final Span span) {
      if (span != null) {
        span.end();
      }
    }
  }
}
