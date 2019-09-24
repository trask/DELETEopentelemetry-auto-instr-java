package datadog.trace.instrumentation.servlet3;

import static datadog.trace.instrumentation.servlet3.Servlet3Decorator.DECORATE;

import datadog.trace.agent.tooling.AttributeNames;
import io.opentelemetry.trace.Span;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class TagSettingAsyncListener implements AsyncListener {
  private final AtomicBoolean activated;
  private final Span span;

  public TagSettingAsyncListener(final AtomicBoolean activated, final Span span) {
    this.activated = activated;
    this.span = span;
  }

  @Override
  public void onComplete(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onTimeout(final AsyncEvent event) throws IOException {
    if (activated.compareAndSet(false, true)) {
      span.setAttribute(AttributeNames.ERROR, true);
      span.setAttribute("timeout", event.getAsyncContext().getTimeout());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onError(final AsyncEvent event) throws IOException {
    if (event.getThrowable() != null && activated.compareAndSet(false, true)) {
      DECORATE.onResponse(span, (HttpServletResponse) event.getSuppliedResponse());
      if (((HttpServletResponse) event.getSuppliedResponse()).getStatus()
          == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        span.setAttribute(AttributeNames.HTTP_STATUS, 500);
      }
      DECORATE.onError(span, event.getThrowable());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  /** Transfer the listener over to the new context. */
  @Override
  public void onStartAsync(final AsyncEvent event) throws IOException {
    event.getAsyncContext().addListener(this);
  }
}
