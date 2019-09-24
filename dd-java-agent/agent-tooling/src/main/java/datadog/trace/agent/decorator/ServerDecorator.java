package datadog.trace.agent.decorator;

import datadog.trace.agent.tooling.Config;
import io.opentelemetry.trace.Span;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public void afterStart(final Span span) {
    assert span != null;
    span.setAttribute(Config.LANGUAGE_TAG_KEY, Config.LANGUAGE_TAG_VALUE);
    super.afterStart(span);
  }
}
