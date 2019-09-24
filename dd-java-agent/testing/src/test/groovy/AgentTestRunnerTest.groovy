import com.google.common.reflect.ClassPath
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.SpockRunner
import datadog.trace.agent.test.utils.ClasspathUtils
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.agent.tooling.Constants
import io.opentelemetry.trace.Span
import spock.lang.Shared

import java.lang.reflect.Field

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.agent.tooling.Config.TRACE_CLASSES_EXCLUDE

class AgentTestRunnerTest extends AgentTestRunner {
  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null
  private static final boolean AGENT_INSTALLED_IN_CLINIT

  @Shared
  private Class sharedSpanClass

  static {
    ConfigUtils.updateConfig {
      System.setProperty("dd." + TRACE_CLASSES_EXCLUDE, "config.exclude.packagename.*, config.exclude.SomeClass,config.exclude.SomeClass\$NestedClass")
    }

    // when test class initializes, opentracing should be set up, but not the agent.
    AGENT_INSTALLED_IN_CLINIT = getAgentTransformer() != null
  }

  def setupSpec() {
    sharedSpanClass = Span
  }

  def "spock runner bootstrap prefixes correct for test setup"() {
    expect:
    SpockRunner.BOOTSTRAP_PACKAGE_PREFIXES_COPY == Constants.BOOTSTRAP_PACKAGE_PREFIXES
  }

  def "classpath setup"() {
    setup:
    final List<String> bootstrapClassesIncorrectlyLoaded = []
    for (ClassPath.ClassInfo info : ClasspathUtils.getTestClasspath().getAllClasses()) {
      for (int i = 0; i < Constants.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
        if (info.getName().startsWith(Constants.BOOTSTRAP_PACKAGE_PREFIXES[i])) {
          Class<?> bootstrapClass = Class.forName(info.getName())
          if (bootstrapClass.getClassLoader() != BOOTSTRAP_CLASSLOADER) {
            bootstrapClassesIncorrectlyLoaded.add(bootstrapClass)
          }
          break
        }
      }
    }

    expect:
    // shared OT classes should cause no trouble
    // TODO trask
    // sharedSpanClass.getClassLoader() == BOOTSTRAP_CLASSLOADER
    // Tracer.getClassLoader() == BOOTSTRAP_CLASSLOADER
    // !AGENT_INSTALLED_IN_CLINIT
    // getTestTracer() == GlobalTracerUtils.getUnderlyingGlobalTracer()
    // getAgentTransformer() != null
    // GlobalTracerUtils.getUnderlyingGlobalTracer() == datadog.trace.api.GlobalTracer.get()
    bootstrapClassesIncorrectlyLoaded == []
  }

  def "logging works"() {
    when:
    org.slf4j.LoggerFactory.getLogger(AgentTestRunnerTest).debug("hello")
    then:
    noExceptionThrown()
  }

  def "test unblocked by completed span"() {
    setup:
    runUnderTrace("parent") {
      runUnderTrace("child") {}
      // TODO trask
      // blockUntilChildSpansFinished(1)
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          spanName "parent"
          parent()
        }
        span(1) {
          spanName "child"
          childOf(span(0))
        }
      }
    }
  }

  private static getAgentTransformer() {
    Field f
    try {
      f = AgentTestRunner.getDeclaredField("activeTransformer")
      f.setAccessible(true)
      return f.get(null)
    } finally {
      f.setAccessible(false)
    }
  }
}
