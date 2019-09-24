package datadog.trace.agent.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.proto.trace.v1.Span

import static SpanAssert.assertSpan

class TraceAssert {
  private final List<Span> trace
  private final int size
  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(trace) {
    this.trace = trace
    size = trace.size()
  }

  static void assertTrace(List<Span> trace, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assert trace.size() == expectedSize
    def asserter = new TraceAssert(trace)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
  }

  Span span(int index) {
    trace.get(index)
  }

  void span(int index, @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    if (trace.size() != size) {
      throw new ConcurrentModificationException("Trace modified during assertion")
    }
    assertedIndexes.add(index)
    assertSpan(trace.get(index), spec)
  }

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == size
  }
}
