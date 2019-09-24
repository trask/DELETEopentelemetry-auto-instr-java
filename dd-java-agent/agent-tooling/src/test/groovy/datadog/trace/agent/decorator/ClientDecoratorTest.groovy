package datadog.trace.agent.decorator

import datadog.trace.agent.tooling.AttributeNames
import io.opentelemetry.trace.Span

class ClientDecoratorTest extends BaseDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    if (serviceName != null) {
      1 * span.setAttribute(AttributeNames.SERVICE_NAME, serviceName)
    }
    1 * span.setAttribute(AttributeNames.COMPONENT, "test-component")
    1 * span.setAttribute(AttributeNames.SPAN_TYPE, decorator.spanType())
    1 * span.setAttribute(AttributeNames.ANALYTICS_SAMPLE_RATE, 1.0)
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test beforeFinish"() {
    when:
    newDecorator("test-service").beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return newDecorator("test-service")
  }

  def newDecorator(String serviceName) {
    return new ClientDecorator() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String service() {
        return serviceName
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
