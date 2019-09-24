package datadog.trace.agent.decorator;

import datadog.trace.agent.tooling.AttributeNames;
import io.opentelemetry.trace.Span;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  @Override
  public void afterStart(final Span span) {
    assert span != null;
    if (service() != null) {
      span.setAttribute(AttributeNames.SERVICE_NAME, service());
    }
    super.afterStart(span);
  }
}
