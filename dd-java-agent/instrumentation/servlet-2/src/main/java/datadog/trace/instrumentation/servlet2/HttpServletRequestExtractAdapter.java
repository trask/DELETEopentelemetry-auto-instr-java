package datadog.trace.instrumentation.servlet2;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.servlet.http.HttpServletRequest;

/**
 * Tracer extract adapter for {@link HttpServletRequest}.
 *
 * @author Pavol Loffay
 */
public class HttpServletRequestExtractAdapter implements HttpTextFormat.Getter<HttpServletRequest> {

  public static HttpServletRequestExtractAdapter INSTANCE = new HttpServletRequestExtractAdapter();

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
