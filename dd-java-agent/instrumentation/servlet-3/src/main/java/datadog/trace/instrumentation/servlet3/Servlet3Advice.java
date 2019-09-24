package datadog.trace.instrumentation.servlet3;

import static datadog.trace.agent.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.servlet3.Servlet3Decorator.DECORATE;

import datadog.trace.agent.tooling.AttributeNames;
import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.agent.tooling.SpanInScope;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet3Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object servlet, @Advice.Argument(0) final ServletRequest req) {
    final Object spanAttr = req.getAttribute(DD_SPAN_ATTRIBUTE);
    if (!(req instanceof HttpServletRequest) || spanAttr != null) {
      // Tracing might already be applied by the FilterChain.  If so ignore this.
      return null;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) req;
    final SpanContext extractedContext =
        GlobalTracer.get()
            .getHttpTextFormat()
            .extract(httpServletRequest, HttpServletRequestExtractAdapter.INSTANCE);

    final Span span =
        GlobalTracer.get().spanBuilder("servlet.request").setParent(extractedContext).startSpan();
    span.setAttribute("span.origin.type", servlet.getClass().getName());

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    // TODO revisit async propagation / continuation

    req.setAttribute(DD_SPAN_ATTRIBUTE, span);
    return SpanInScope.create(span, GlobalTracer.get().withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final SpanInScope scope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Object spanAttr = request.getAttribute(DD_SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span && request instanceof HttpServletRequest) {
      final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
      if (principal != null) {
        ((Span) spanAttr).setAttribute(AttributeNames.USER_NAME, principal.getName());
      }
    }

    if (scope != null) {
      if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;
        final Span span = scope.span();

        if (throwable != null) {
          DECORATE.onResponse(span, resp);
          if (resp.getStatus() == HttpServletResponse.SC_OK) {
            // exception is thrown in filter chain, but status code is incorrect
            span.setAttribute(AttributeNames.HTTP_STATUS, 500);
          }
          DECORATE.onError(span, throwable);
          DECORATE.beforeFinish(span);
          req.removeAttribute(DD_SPAN_ATTRIBUTE);
          span.end(); // Finish the span manually since finishSpanOnClose was false
        } else {
          final AtomicBoolean activated = new AtomicBoolean(false);
          if (req.isAsyncStarted()) {
            try {
              req.getAsyncContext().addListener(new TagSettingAsyncListener(activated, span));
            } catch (final IllegalStateException e) {
              // org.eclipse.jetty.server.Request may throw an exception here if request became
              // finished after check above. We just ignore that exception and move on.
            }
          }
          // Check again in case the request finished before adding the listener.
          if (!req.isAsyncStarted() && activated.compareAndSet(false, true)) {
            DECORATE.onResponse(span, resp);
            DECORATE.beforeFinish(span);
            req.removeAttribute(DD_SPAN_ATTRIBUTE);
            span.end(); // Finish the span manually since finishSpanOnClose was false
          }
        }
        scope.close();
      }
    }
  }
}
