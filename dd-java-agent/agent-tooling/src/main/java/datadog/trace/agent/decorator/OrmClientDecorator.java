package datadog.trace.agent.decorator;

import datadog.trace.agent.tooling.AttributeNames;
import io.opentelemetry.trace.Span;

public abstract class OrmClientDecorator extends DatabaseClientDecorator {

  public abstract String entityName(final Object entity);

  public Span onOperation(final Span span, final Object entity) {

    assert span != null;
    if (entity != null) {
      final String name = entityName(entity);
      if (name != null) {
        span.setAttribute(AttributeNames.RESOURCE_NAME, name);
      } // else we keep any existing resource.
    }
    return span;
  }
}
