import datadog.trace.agent.tooling.GlobalTracer;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaAsyncChild extends ForkJoinTask implements Runnable, Callable {
  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;

  public JavaAsyncChild() {
    this(true, false);
  }

  @Override
  public Object getRawResult() {
    return null;
  }

  @Override
  protected void setRawResult(final Object value) {}

  @Override
  protected boolean exec() {
    runImpl();
    return true;
  }

  public JavaAsyncChild(final boolean doTraceableWork, final boolean blockThread) {
    this.doTraceableWork = doTraceableWork;
    this.blockThread = new AtomicBoolean(blockThread);
  }

  public void unblock() {
    blockThread.set(false);
  }

  @Override
  public void run() {
    runImpl();
  }

  @Override
  public Object call() throws Exception {
    runImpl();
    return null;
  }

  private void runImpl() {
    while (blockThread.get()) {
      // busy-wait to block thread
    }
    if (doTraceableWork) {
      asyncChild();
    }
  }

  private void asyncChild() {
    GlobalTracer.get().spanBuilder("asyncChild").startSpan().end();
  }
}
