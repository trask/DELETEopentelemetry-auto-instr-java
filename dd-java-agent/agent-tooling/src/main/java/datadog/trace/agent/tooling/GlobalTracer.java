package datadog.trace.agent.tooling;

import io.opentelemetry.trace.Tracer;

public class GlobalTracer {

  private static volatile Tracer tracer;

  public static Tracer get() {
    return tracer;
  }

  public static void set(Tracer tracer) {
    GlobalTracer.tracer = tracer;
  }
}
