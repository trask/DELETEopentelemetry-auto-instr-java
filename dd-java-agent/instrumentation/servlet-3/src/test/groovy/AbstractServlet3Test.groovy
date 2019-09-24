import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.agent.tooling.AttributeNames
import datadog.trace.agent.tooling.DDSpanTypes
import datadog.trace.instrumentation.servlet3.Servlet3Decorator
import io.opentelemetry.trace.Span

import javax.servlet.Servlet
import okhttp3.Request
import org.apache.catalina.core.ApplicationFilterChain

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AbstractServlet3Test<SERVER, CONTEXT> extends HttpServerTest<SERVER, Servlet3Decorator> {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port/$context/")
  }

  @Override
  Servlet3Decorator decorator() {
    return Servlet3Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  // FIXME: Add authentication tests back in...
//  @Shared
//  protected String user = "user"
//  @Shared
//  protected String pass = "password"

  abstract String getContext()

  Class<Servlet> servlet = servlet()

  abstract Class<Servlet> servlet()

  abstract void addServlet(CONTEXT context, String path, Class<Servlet> servlet)

  protected void setupServlets(CONTEXT context) {
    def servlet = servlet()

    addServlet(context, SUCCESS.path, servlet)
    addServlet(context, ERROR.path, servlet)
    addServlet(context, EXCEPTION.path, servlet)
    addServlet(context, REDIRECT.path, servlet)
    addServlet(context, AUTH_REQUIRED.path, servlet)
  }

  protected ServerEndpoint lastRequest

  @Override
  Request.Builder request(ServerEndpoint uri, String method, String body) {
    lastRequest = uri
    super.request(uri, method, body)
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      spanKind Span.Kind.SERVER
      spanName expectedOperationName()
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$AttributeNames.SPAN_TYPE" DDSpanTypes.HTTP_SERVER

        "servlet.context" "/$context"
        "span.origin.type" { it == servlet.name || it == ApplicationFilterChain.name }

        defaultTags(true)
        "$AttributeNames.COMPONENT" serverDecorator.component()
        if (endpoint.errored) {
          "$AttributeNames.ERROR" true
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        "$AttributeNames.HTTP_STATUS" endpoint.status
        "$AttributeNames.HTTP_URL" "${endpoint.resolve(address)}"
        "$AttributeNames.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$AttributeNames.PEER_PORT" Integer
        "$AttributeNames.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$AttributeNames.HTTP_METHOD" method
      }
    }
  }
}
