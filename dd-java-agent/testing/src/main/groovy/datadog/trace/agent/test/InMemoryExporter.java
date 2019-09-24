package datadog.trace.agent.test;

import com.google.common.base.Stopwatch;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InMemoryExporter implements SpanExporter {

  private final List<List<Span>> traces = new CopyOnWriteArrayList<>();

  private final Object lock = new Object();

  @Override
  public ResultCode export(List<Span> spans) {
    synchronized (traces) {
      for (Span span : spans) {
        boolean found = false;
        for (List<Span> trace : traces) {
          if (trace.get(0).getTraceId().equals(span.getTraceId())) {
            // TODO trask: flip everything?
            // adding to front, following prior behavior in PendingTrace, to minimize changes to
            // tests
            trace.add(0, span);
            found = true;
            break;
          }
        }
        if (!found) {
          List<Span> trace = new CopyOnWriteArrayList<>();
          trace.add(span);
          traces.add(trace);
        }
      }
    }
    return ResultCode.SUCCESS;
  }

  public List<List<Span>> getTraces() {
    return traces;
  }

  public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
    synchronized (lock) {
      long remainingWaitMillis = TimeUnit.SECONDS.toMillis(20);
      while (traces.size() < number && remainingWaitMillis > 0) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        lock.wait(remainingWaitMillis);
        remainingWaitMillis -= stopwatch.elapsed(TimeUnit.MILLISECONDS);
      }
      if (traces.size() < number) {
        throw new TimeoutException(
            "Timeout waiting for " + number + " trace(s), found " + traces.size() + " trace(s)");
      }
    }
  }

  @Override
  public void shutdown() {}
}
