package datadog.trace.agent.tooling;

import io.opentelemetry.sdk.trace.TracerSdk;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register a global tracer if no global tracer is already registered. */
  public static synchronized void installGlobalTracer() {
    if (Config.get().isTraceEnabled()) {
      GlobalTracer.set(new TracerSdk());
    } else {
      log.debug("Tracing is disabled, not installing GlobalTracer.");
    }
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        io.opentelemetry.OpenTelemetry.class.getName()
            + " loaded on "
            + io.opentelemetry.OpenTelemetry.class.getClassLoader());
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
