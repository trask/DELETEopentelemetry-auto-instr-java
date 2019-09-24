package datadog.trace.agent.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.Sets;
import datadog.trace.agent.test.asserts.InMemoryExporterAssert;
import datadog.trace.agent.test.utils.ConfigUtils;
import datadog.trace.agent.tooling.AgentInstaller;
import datadog.trace.agent.tooling.GlobalTracer;
import datadog.trace.agent.tooling.Instrumenter;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import io.opentelemetry.sdk.trace.TracerSdk;
import io.opentelemetry.sdk.trace.export.SimpleSampledSpansProcessor;
import io.opentelemetry.trace.Tracer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.SpecMetadata;
import spock.lang.Specification;

/**
 * A spock test runner which automatically applies instrumentation and exposes a global trace
 * writer.
 *
 * <p>To use, write a regular spock test, but extend this class instead of {@link
 * spock.lang.Specification}. <br>
 * This will cause the following to occur before test startup:
 *
 * <ul>
 *   <li>All {@link Instrumenter}s on the test classpath will be applied. Matching preloaded classes
 *       will be retransformed.
 *   <li>{@link AgentTestRunner#getTestTracer()} will be registered with the global tracer and
 *       available in an initialized state.
 * </ul>
 */
@RunWith(SpockRunner.class)
@SpecMetadata(filename = "AgentTestRunner.java", line = 0)
@Slf4j
public abstract class AgentTestRunner extends Specification {
  private static final long TIMEOUT_MILLIS = 10 * 1000;
  /**
   * For test runs, agent's global tracer will report to this list writer.
   *
   * <p>Before the start of each test the reported traces will be reset.
   */
  public static final InMemoryExporter TEST_EXPORTER;

  // having a reference to io.opentelemetry.trace.Tracer in test field
  // loads opentelemetry before bootstrap classpath is setup
  // so we declare tracer as an object and cast when needed.
  protected static final Object TEST_TRACER;

  protected static final Set<String> TRANSFORMED_CLASSES = Sets.newConcurrentHashSet();
  private static final AtomicInteger INSTRUMENTATION_ERROR_COUNT = new AtomicInteger();
  private static final TestRunnerListener TEST_LISTENER = new TestRunnerListener();

  private static final Instrumentation INSTRUMENTATION;
  private static volatile ClassFileTransformer activeTransformer = null;

  static {
    INSTRUMENTATION = ByteBuddyAgent.getInstrumentation();

    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("datadog")).setLevel(Level.DEBUG);

    ConfigUtils.makeConfigInstanceModifiable();

    TEST_EXPORTER = new InMemoryExporter();
    TracerSdk tracer = new TracerSdk();
    tracer.addSpanProcessor(SimpleSampledSpansProcessor.newBuilder(TEST_EXPORTER).build());
    GlobalTracer.set(tracer);
    TEST_TRACER = tracer;
  }

  protected static Tracer getTestTracer() {
    return (Tracer) TEST_TRACER;
  }

  /**
   * Invoked when Bytebuddy encounters an instrumentation error. Fails the test by default.
   *
   * <p>Override to skip specific expected errors.
   *
   * @return true if the test should fail because of this error.
   */
  protected boolean onInstrumentationError(
      final String typeName,
      final ClassLoader classLoader,
      final JavaModule module,
      final boolean loaded,
      final Throwable throwable) {
    log.error(
        "Unexpected instrumentation error when instrumenting {} on {}",
        typeName,
        classLoader,
        throwable);
    return true;
  }

  /**
   * @param className name of the class being loaded
   * @param classLoader classloader class is being defined on
   * @return true if the class under load should be transformed for this test.
   */
  protected boolean shouldTransformClass(final String className, final ClassLoader classLoader) {
    return true;
  }

  @BeforeClass
  public static synchronized void agentSetup() throws Exception {
    if (null != activeTransformer) {
      throw new IllegalStateException("transformer already in place: " + activeTransformer);
    }

    final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(AgentTestRunner.class.getClassLoader());
      assert ServiceLoader.load(Instrumenter.class).iterator().hasNext()
          : "No instrumentation found";
      activeTransformer = AgentInstaller.installBytebuddyAgent(INSTRUMENTATION, TEST_LISTENER);
    } finally {
      Thread.currentThread().setContextClassLoader(contextLoader);
    }

    INSTRUMENTATION_ERROR_COUNT.set(0);
  }

  /**
   * Normally {@code @BeforeClass} is run only on static methods, but spock allows us to run it on
   * instance methods. Note: this means there is a 'special' instance of test class that is not used
   * to run any tests, but instead is just used to run this method once.
   */
  @BeforeClass
  public void setupBeforeTests() {
    TEST_LISTENER.activateTest(this);
  }

  @Before
  public void beforeTest() {
    assert !getTestTracer().getCurrentSpan().getContext().isValid()
        : "Span is active before test has started: " + getTestTracer().getCurrentSpan();
    log.debug("Starting test: '{}'", getSpecificationContext().getCurrentIteration().getName());
    TEST_EXPORTER.getTraces().clear();
  }

  /** See comment for {@code #setupBeforeTests} above. */
  @AfterClass
  public void cleanUpAfterTests() {
    TEST_LISTENER.deactivateTest(this);
  }

  @AfterClass
  public static synchronized void agentCleanup() {
    if (null != activeTransformer) {
      INSTRUMENTATION.removeTransformer(activeTransformer);
      activeTransformer = null;
    }
    // Cleanup before assertion.
    assert INSTRUMENTATION_ERROR_COUNT.get() == 0
        : INSTRUMENTATION_ERROR_COUNT.get() + " Instrumentation errors during test";
  }

  public static void assertTraces(
      final int size,
      @ClosureParams(
              value = SimpleType.class,
              options = "datadog.trace.agent.test.asserts.InMemoryExporterAssert")
          @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
          final Closure spec) {
    InMemoryExporterAssert.assertTraces(TEST_EXPORTER, size, spec);
  }

  public static class TestRunnerListener implements AgentBuilder.Listener {
    private static final List<AgentTestRunner> activeTests = new CopyOnWriteArrayList<>();

    public void activateTest(final AgentTestRunner testRunner) {
      activeTests.add(testRunner);
    }

    public void deactivateTest(final AgentTestRunner testRunner) {
      activeTests.remove(testRunner);
    }

    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {
      for (final AgentTestRunner testRunner : activeTests) {
        if (!testRunner.shouldTransformClass(typeName, classLoader)) {
          throw new AbortTransformationException(
              "Aborting transform for class name = " + typeName + ", loader = " + classLoader);
        }
      }
    }

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final DynamicType dynamicType) {
      TRANSFORMED_CLASSES.add(typeDescription.getActualName());
    }

    @Override
    public void onIgnored(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      if (!(throwable instanceof AbortTransformationException)) {
        for (final AgentTestRunner testRunner : activeTests) {
          if (testRunner.onInstrumentationError(typeName, classLoader, module, loaded, throwable)) {
            INSTRUMENTATION_ERROR_COUNT.incrementAndGet();
            break;
          }
        }
      }
    }

    @Override
    public void onComplete(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    /** Used to signal that a transformation was intentionally aborted and is not an error. */
    public static class AbortTransformationException extends RuntimeException {
      public AbortTransformationException() {
        super();
      }

      public AbortTransformationException(final String message) {
        super(message);
      }
    }
  }
}
