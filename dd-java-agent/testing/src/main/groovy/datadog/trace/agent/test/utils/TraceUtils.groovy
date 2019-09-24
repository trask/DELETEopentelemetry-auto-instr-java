package datadog.trace.agent.test.utils

import datadog.trace.agent.decorator.BaseDecorator
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.tooling.GlobalTracer
import io.opentelemetry.context.Scope
import io.opentelemetry.proto.trace.v1.Span
import lombok.SneakyThrows

import java.util.concurrent.Callable

class TraceUtils {

  private static final BaseDecorator DECORATOR = new BaseDecorator() {
    protected String[] instrumentationNames() {
      return new String[0]
    }

    protected String spanType() {
      return null
    }

    protected String component() {
      return null
    }
  }

  @SneakyThrows
  static <T> T runUnderTrace(final String rootOperationName, final Callable<T> r) {
    final io.opentelemetry.trace.Span span = GlobalTracer.get().spanBuilder(rootOperationName).startSpan()
    final Scope scope = GlobalTracer.get().withSpan(span)
    DECORATOR.afterStart(span)
    // TODO trask
    // ((TraceScope) scope).setAsyncPropagation(true)

    try {
      return r.call()
    } catch (final Exception e) {
      DECORATOR.onError(span, e)
      throw e
    } finally {
      DECORATOR.beforeFinish(span)
      scope.close()
      span.end()
    }
  }

  static basicSpan(TraceAssert trace, int index, String spanName, Object parentSpan = null, Throwable exception = null) {
    basicSpan(trace, index, spanName, spanName, parentSpan, exception)
  }

  static basicSpan(TraceAssert trace, int index, String operation, String resource, Span parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf(parentSpan)
      }
      spanName operation
      tags {
        "resource.name" resource
        defaultTags()
        if (exception) {
          errorTags(exception.class, exception.message)
        }
      }
    }
  }
}
