package datadog.trace.agent.decorator

import datadog.trace.agent.tooling.AttributeNames
import datadog.trace.agent.tooling.Config
import io.opentelemetry.trace.Span

class ServerDecoratorTest extends BaseDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    def decorator = newDecorator()
    when:
    decorator.afterStart(span)

    then:
    1 * span.setAttribute(Config.LANGUAGE_TAG_KEY, Config.LANGUAGE_TAG_VALUE)
    1 * span.setAttribute(AttributeNames.COMPONENT, "test-component")
    1 * span.setAttribute(AttributeNames.SPAN_TYPE, decorator.spanType())
    if (decorator.traceAnalyticsEnabled) {
      1 * span.setAttribute(AttributeNames.ANALYTICS_SAMPLE_RATE, 1.0)
    }
    0 * _
  }

  def "test beforeFinish"() {
    when:
    newDecorator().beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return new ServerDecorator() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String spanType() {
        return "test-type"
      }

      @Override
      protected String component() {
        return "test-component"
      }

      protected boolean traceAnalyticsDefault() {
        return true
      }
    }
  }
}
