import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.tooling.GlobalTracer
import io.opentelemetry.proto.trace.v1.Span

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

/**
 * Note: ideally this should live with the rest of ExecutorInstrumentationTest,
 * but this code needs java8 so we put it here for now.
 */
class CompletableFutureTest extends AgentTestRunner {

  def "CompletableFuture test"() {
    setup:
    def pool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def differentPool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def supplier = new Supplier<String>() {
      @Override
      String get() {
        def span = GlobalTracer.get().spanBuilder("supplier").startSpan()
        sleep(1000)
        span.end()
        return "a"
      }
    }

    def function = new Function<String, String>() {
      @Override
      String apply(String s) {
        GlobalTracer.get().spanBuilder("function").startSpan().end()
        return s + "c"
      }
    }

    def future = new Supplier<CompletableFuture<String>>() {
      @Override
      CompletableFuture<String> get() {
        def span = GlobalTracer.get().spanBuilder("parent").startSpan()
        def scope = GlobalTracer.get().withSpan(span)
        def future = CompletableFuture.supplyAsync(supplier, pool)
          .thenCompose({ s -> CompletableFuture.supplyAsync(new AppendingSupplier(s), differentPool) })
          .thenApply(function)
        scope.close()
        span.end()
        return future
      }
    }.get()

    def result = future.get()

    TEST_EXPORTER.waitForTraces(1)
    List<Span> trace = TEST_EXPORTER.traces.get(0)

    expect:
    result == "abc"

    TEST_EXPORTER.traces.size() == 1
    trace.size() == 4
    trace.get(0).name == "parent"
    trace.get(1).name == "function"
    trace.get(1).parentSpanId == trace.get(0).spanId
    trace.get(2).name == "appendingSupplier"
    trace.get(2).parentSpanId == trace.get(0).spanId
    trace.get(3).name == "supplier"
    trace.get(3).parentSpanId == trace.get(0).spanId

    cleanup:
    pool?.shutdown()
    differentPool?.shutdown()
  }

  class AppendingSupplier implements Supplier<String> {
    String letter

    AppendingSupplier(String letter) {
      this.letter = letter
    }

    @Override
    String get() {
      GlobalTracer.get().spanBuilder("appendingSupplier").startSpan().end()
      return letter + "b"
    }
  }

}
