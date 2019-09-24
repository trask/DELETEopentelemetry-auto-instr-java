package datadog.trace.agent.decorator;

import datadog.trace.agent.tooling.AttributeNames;
import datadog.trace.agent.tooling.Config;
import io.opentelemetry.trace.Span;

public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  @Override
  public void afterStart(final Span span) {
    assert span != null;
    span.setAttribute(AttributeNames.DB_TYPE, dbType());
    super.afterStart(span);
  }

  /**
   * This should be called when the connection is being used, not when it's created.
   *
   * @param span
   * @param connection
   * @return
   */
  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      final String dbUser = dbUser(connection);
      if (dbUser != null) {
        span.setAttribute(AttributeNames.DB_USER, dbUser);
      }
      final String instanceName = dbInstance(connection);
      if (instanceName != null) {
        span.setAttribute(AttributeNames.DB_INSTANCE, instanceName);
        if (Config.get().isDbClientSplitByInstance()) {
          span.setAttribute(AttributeNames.SERVICE_NAME, instanceName);
        }
      }
    }
    return span;
  }

  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    if (statement != null) {
      span.setAttribute(AttributeNames.DB_STATEMENT, statement);
    }
    return span;
  }
}
