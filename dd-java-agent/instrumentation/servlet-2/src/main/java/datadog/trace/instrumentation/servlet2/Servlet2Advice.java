package datadog.trace.instrumentation.servlet2;

import static datadog.trace.agent.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.servlet2.Servlet2Decorator.DECORATE;

import datadog.trace.agent.tooling.AttributeNames;
import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.agent.tooling.SpanInScope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.security.Principal;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class Servlet2Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanInScope startSpan(
      @Advice.This final Object servlet,
      @Advice.Argument(0) final ServletRequest req,
      @Advice.Argument(value = 1, readOnly = false, typing = Assigner.Typing.DYNAMIC)
          ServletResponse resp) {
    final Object spanAttr = req.getAttribute(DD_SPAN_ATTRIBUTE);
    if (!(req instanceof HttpServletRequest) || spanAttr != null) {
      // Tracing might already be applied by the FilterChain.  If so ignore this.
      return null;
    }

    if (resp instanceof HttpServletResponse) {
      resp = new StatusSavingHttpServletResponseWrapper((HttpServletResponse) resp);
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) req;

    Span.Builder spanBuilder =
        GlobalTracer.get().spanBuilder("servlet.request").setSpanKind(Span.Kind.SERVER);

    HttpTextFormat<SpanContext> httpTextFormat = GlobalTracer.get().getHttpTextFormat();
    try {
      spanBuilder.setParent(
          httpTextFormat.extract(httpServletRequest, HttpServletRequestExtractAdapter.INSTANCE));
    } catch (IllegalArgumentException e) {
      // TODO trask: open telemetry api that doesn't throw exception?
    }

    final Span span = spanBuilder.startSpan();

    span.setAttribute("span.origin.type", servlet.getClass().getName());
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    // TODO trask
    // if (scope instanceof TraceScope) {
    //   ((TraceScope) scope).setAsyncPropagation(true);
    // }

    req.setAttribute(DD_SPAN_ATTRIBUTE, span);
    return SpanInScope.create(span, GlobalTracer.get().withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final SpanInScope spanInScope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Span currentSpan = GlobalTracer.get().getCurrentSpan();
    if (currentSpan != null) {
      if (request instanceof HttpServletRequest) {
        final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
        if (principal != null) {
          currentSpan.setAttribute(AttributeNames.USER_NAME, principal.getName());
        }
      }
    }

    if (spanInScope != null) {
      Span span = spanInScope.span();
      DECORATE.onResponse(span, response);
      if (throwable != null) {
        if (response instanceof StatusSavingHttpServletResponseWrapper
            && ((StatusSavingHttpServletResponseWrapper) response).status
                == HttpServletResponse.SC_OK) {
          // exception was thrown but status code wasn't set
          span.setAttribute(AttributeNames.HTTP_STATUS, 500);
        }
        DECORATE.onError(span, throwable);
      }
      DECORATE.beforeFinish(span);

      // TODO trask
      // if (span instanceof TraceScope) {
      //   ((TraceScope) span).setAsyncPropagation(false);
      // }
      spanInScope.close();
      span.end();
    }
  }
}
