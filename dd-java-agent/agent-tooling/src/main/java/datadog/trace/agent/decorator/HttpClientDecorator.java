package datadog.trace.agent.decorator;

import datadog.trace.agent.tooling.AttributeNames;
import datadog.trace.agent.tooling.Config;
import datadog.trace.agent.tooling.DDSpanTypes;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpClientDecorator<REQUEST, RESPONSE> extends ClientDecorator {

  protected abstract String method(REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract String hostname(REQUEST request);

  protected abstract Integer port(REQUEST request);

  protected abstract Integer status(RESPONSE response);

  @Override
  protected String spanType() {
    return DDSpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String service() {
    return null;
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      String httpMethod = method(request);
      if (httpMethod != null) {
        span.setAttribute(AttributeNames.HTTP_METHOD, httpMethod);
      }

      // Copy of HttpServerDecorator url handling
      try {
        final URI url = url(request);
        if (url != null) {
          final StringBuilder urlNoParams = new StringBuilder();
          if (url.getScheme() != null) {
            urlNoParams.append(url.getScheme());
            urlNoParams.append("://");
          }
          if (url.getHost() != null) {
            urlNoParams.append(url.getHost());
            if (url.getPort() > 0 && url.getPort() != 80 && url.getPort() != 443) {
              urlNoParams.append(":");
              urlNoParams.append(url.getPort());
            }
          }
          final String path = url.getPath();
          if (path.isEmpty()) {
            urlNoParams.append("/");
          } else {
            urlNoParams.append(path);
          }

          span.setAttribute(AttributeNames.HTTP_URL, urlNoParams.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            span.setAttribute(AttributeNames.HTTP_QUERY, url.getQuery());
            span.setAttribute(AttributeNames.HTTP_FRAGMENT, url.getFragment());
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }

      String hostname = hostname(request);
      if (hostname != null) {
        span.setAttribute(AttributeNames.PEER_HOSTNAME, hostname);
      }
      final Integer port = port(request);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      if (port != null && port > 0) {
        span.setAttribute(AttributeNames.PEER_PORT, port);
      }

      if (Config.get().isHttpClientSplitByDomain() && hostname != null) {
        span.setAttribute(AttributeNames.SERVICE_NAME, hostname);
      }
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        span.setAttribute(AttributeNames.HTTP_STATUS, status);

        if (Config.get().getHttpClientErrorStatuses().contains(status)) {
          span.setAttribute(AttributeNames.ERROR, true);
        }
      }
    }
    return span;
  }
}
