package datadog.trace.agent.decorator;

import static java.util.Collections.singletonMap;

import datadog.trace.agent.tooling.AttributeNames;
import datadog.trace.agent.tooling.Config;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

public abstract class BaseDecorator {

  protected final boolean traceAnalyticsEnabled;
  protected final float traceAnalyticsSampleRate;

  protected BaseDecorator() {
    Config config = Config.get();
    final String[] instrumentationNames = instrumentationNames();
    traceAnalyticsEnabled =
        instrumentationNames.length > 0
            && config.isTraceAnalyticsIntegrationEnabled(
                new TreeSet<>(Arrays.asList(instrumentationNames)), traceAnalyticsDefault());
    traceAnalyticsSampleRate = config.getInstrumentationAnalyticsSampleRate(instrumentationNames);
  }

  protected abstract String[] instrumentationNames();

  protected abstract String spanType();

  protected abstract String component();

  protected boolean traceAnalyticsDefault() {
    return false;
  }

  public void afterStart(final Span span) {
    assert span != null;
    String spanType = spanType();
    if (spanType != null) {
      span.setAttribute(AttributeNames.SPAN_TYPE, spanType);
    }
    String component = component();
    if (component != null) {
      span.setAttribute(AttributeNames.COMPONENT, component);
    }
    if (traceAnalyticsEnabled) {
      span.setAttribute(AttributeNames.ANALYTICS_SAMPLE_RATE, traceAnalyticsSampleRate);
    }
  }

  public void beforeFinish(final Span span) {
    assert span != null;
  }

  public void onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setAttribute(AttributeNames.ERROR, true);
      Throwable cause = throwable instanceof ExecutionException ? throwable.getCause() : throwable;
      StringWriter sw = new StringWriter();
      try (PrintWriter pw = new PrintWriter(sw)) {
        cause.printStackTrace(pw);
      }
      span.addEvent(
          "error",
          singletonMap(
              AttributeNames.ERROR_OBJECT, AttributeValue.stringAttributeValue(sw.toString())));
    }
  }

  public Span onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      onPeerConnection(span, remoteConnection.getAddress());

      span.setAttribute(AttributeNames.PEER_HOSTNAME, remoteConnection.getHostName());
      span.setAttribute(AttributeNames.PEER_PORT, remoteConnection.getPort());
    }
    return span;
  }

  public Span onPeerConnection(final Span span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setAttribute(AttributeNames.PEER_HOSTNAME, remoteAddress.getHostName());
      if (remoteAddress instanceof Inet4Address) {
        span.setAttribute(AttributeNames.PEER_HOST_IPV4, remoteAddress.getHostAddress());
      } else if (remoteAddress instanceof Inet6Address) {
        span.setAttribute(AttributeNames.PEER_HOST_IPV6, remoteAddress.getHostAddress());
      }
    }
    return span;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method
   * @return
   */
  public String spanNameForMethod(final Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   *
   * @param clazz
   * @return
   */
  public String spanNameForClass(final Class clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      final String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = clazz.getName().replace(pkgName, "").substring(1);
      }
    }
    return className;
  }
}
