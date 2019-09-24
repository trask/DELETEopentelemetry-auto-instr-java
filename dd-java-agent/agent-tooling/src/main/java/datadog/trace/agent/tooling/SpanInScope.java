package datadog.trace.agent.tooling;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

public class SpanInScope implements Scope {

  private final Span span;
  private final Scope scope;

  public static SpanInScope create(Span span, Scope scope) {
    return new SpanInScope(span, scope);
  }

  private SpanInScope(Span span, Scope scope) {
    this.span = span;
    this.scope = scope;
  }

  public Span span() {
    return span;
  }

  @Override
  public void close() {
    scope.close();
  }
}
