package datadog.trace.agent.test.asserts

import com.google.common.io.BaseEncoding
import com.google.protobuf.ByteString
import io.opentelemetry.proto.trace.v1.Span
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import static TagsAssert.assertTags

class SpanAssert {
  private final Span span
  private final checked = [:]

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(Span span,
                         @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    asserter.assertSpan spec
  }

  void assertSpan(
    @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert'])
    @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def clone = (Closure) spec.clone()
    clone.delegate = this
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(this)
  }

  def spanKind(Span.SpanKind kind) {
    assert span.kind == kind
    checked.spanKind = true
  }

  def assertSpanNameContains(String spanName, String... shouldContainArr) {
    for (String shouldContain : shouldContainArr) {
      assert spanName.contains(shouldContain)
    }
  }

  def spanName(String name) {
    assert span.name == name
    checked.spanName = true
  }

  def spanNameContains(String... spanNameParts) {
    assertSpanNameContains(span.name, spanNameParts)
    checked.spanName = true
  }

  def parent() {
    assert span.parentSpanId.isEmpty()
    checked.parentId = true
  }

  def parentId(String parentId) {
    assert BaseEncoding.base16().lowerCase().encode(span.parentSpanId.toByteArray()) == parentId
    checked.parentId = true
  }

  def parentId(ByteString parentId) {
    assert span.parentSpanId == parentId
    checked.parentId = true
  }

  def traceId(String traceId) {
    assert BaseEncoding.base16().lowerCase().encode(span.traceId.toByteArray()) == traceId
    checked.traceId = true
  }

  def traceId(ByteString traceId) {
    assert span.traceId == traceId
    checked.traceId = true
  }

  def childOf(Span parent) {
    parentId(parent.spanId)
    traceId(parent.traceId)
  }

  void tags(@ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TagsAssert'])
            @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertTags(span, spec)
  }
}
