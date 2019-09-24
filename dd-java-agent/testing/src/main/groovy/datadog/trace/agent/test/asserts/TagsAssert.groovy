package datadog.trace.agent.test.asserts

import datadog.trace.agent.tooling.Config
import io.opentelemetry.proto.trace.v1.AttributeValue
import io.opentelemetry.proto.trace.v1.Span
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import java.util.regex.Pattern

class TagsAssert {
  private final Span.SpanKind kind
  private final String parentSpanId
  private final Map<String, AttributeValue> attributes
  private final Set<String> assertedTags = new TreeSet<>()

  private TagsAssert(Span span) {
    this.kind = span.kind
    this.parentSpanId = span.parentSpanId
    this.attributes = span.attributes.attributeMapMap
  }

  static void assertTags(Span span,
                         @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TagsAssert'])
                         @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new TagsAssert(span)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertTagsAllVerified()
  }

  /**
   * @param distributedRootSpan set to true if current span has a parent span but still considered 'root' for current service
   */
  def defaultTags(boolean distributedRootSpan = false) {
    assertedTags.add("thread.name")
    assertedTags.add("thread.id")
    assertedTags.add(Config.RUNTIME_ID_TAG)
    assertedTags.add(Config.LANGUAGE_TAG_KEY)

    // TODO trask
    return

    assert attributes["thread.name"] != null
    assert attributes["thread.id"] != null

    // FIXME: DQH - Too much conditional logic?  Maybe create specialized methods for client & server cases

    boolean isRoot = ("0" == parentSpanId)
    if (isRoot || distributedRootSpan) {
      assert attributes[Config.RUNTIME_ID_TAG] == Config.get().runtimeId
    } else {
      assert attributes[Config.RUNTIME_ID_TAG] == null
    }

    boolean isServer = (kind == Span.SpanKind.SERVER)
    if (isRoot || distributedRootSpan || isServer) {
      assert attributes[Config.LANGUAGE_TAG_KEY] == Config.LANGUAGE_TAG_VALUE
    } else {
      assert attributes[Config.LANGUAGE_TAG_KEY] == null
    }
  }

  def errorTags(Class<Throwable> errorType) {
    errorTags(errorType, null)
  }

  def errorTags(Class<Throwable> errorType, message) {
    tag("error", true)
    // TODO trask: these used to be copied over from span.addEvent() to span, need EventAssert now?
    // tag("error.type", errorType.name)
    // tag("error.stack", String)

    // if (message != null) {
    //   tag("error.msg", message)
    // }
  }

  def tag(String name, value) {
    if (value == null) {
      return
    }
    assertedTags.add(name)
    if (value instanceof Pattern) {
      assert attributes[name] != null
      assert attributes[name].stringValue =~ value
    } else if (value instanceof Class) {
      assert ((Class) value).isInstance(valueFromAttributeValue(attributes[name]))
    } else if (value instanceof Closure) {
      assert ((Closure) value).call(valueFromAttributeValue(attributes[name]))
    } else {
      assert attributes[name] != null
      assert valueFromAttributeValue(attributes[name]) == value
    }
  }

  def tag(String name) {
    return attributes[name]
  }

  def methodMissing(String name, args) {
    if (args.length == 0) {
      throw new IllegalArgumentException(args.toString())
    }
    tag(name, args[0])
  }

  void assertTagsAllVerified() {
    def set = new TreeMap<>(attributes).keySet()
    set.removeAll(assertedTags)
    // The primary goal is to ensure the set is empty.
    // tags and assertedTags are included via an "always true" comparison
    // so they provide better context in the error message.
    assert attributes.entrySet() != assertedTags && set.isEmpty()
  }

  private Object valueFromAttributeValue(attributeValue) {
    if (attributeValue == null) {
      return null
    }
    switch (attributeValue.valueCase) {
      case AttributeValue.ValueCase.BOOL_VALUE:
        return attributeValue.boolValue
      case AttributeValue.ValueCase.INT_VALUE:
        return attributeValue.intValue
      case AttributeValue.ValueCase.DOUBLE_VALUE:
        return attributeValue.doubleValue
      case AttributeValue.ValueCase.STRING_VALUE:
        return attributeValue.stringValue
      default:
        throw new IllegalStateException(attributeValue.valueCase)
    }
  }
}
