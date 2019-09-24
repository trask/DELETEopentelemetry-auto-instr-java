package datadog.trace.instrumentation.servlet3;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.servlet.http.HttpServletRequest;

/** Inject into request attributes since the request headers can't be modified. */
public class HttpServletRequestInjectAdapter implements HttpTextFormat.Setter<HttpServletRequest> {

  public static HttpServletRequestInjectAdapter INSTANCE = new HttpServletRequestInjectAdapter();

  @Override
  public void put(HttpServletRequest carrier, String key, String value) {
    carrier.setAttribute(key, value);
  }
}
