import datadog.trace.agent.test.asserts.InMemoryExporterAssert
import datadog.trace.agent.tooling.AttributeNames
import datadog.trace.agent.tooling.DDSpanTypes
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.proto.trace.v1.Span
import org.apache.catalina.core.ApplicationFilterChain
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.Servlet
import javax.servlet.http.HttpServletRequest

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan

abstract class JettyServlet3Test extends AbstractServlet3Test<Server, ServletContextHandler> {

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Server startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      if (it.hasProperty("resolveNames")) {
        it.resolveNames = true  // get localhost instead of 127.0.0.1
      }
    }

    ServletContextHandler servletContext = new ServletContextHandler(null, "/$context")
    servletContext.errorHandler = new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    }
//    setupAuthentication(jettyServer, servletContext)
    setupServlets(servletContext)
    jettyServer.setHandler(servletContext)

    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContext() {
    return "jetty-context"
  }

  @Override
  void addServlet(ServletContextHandler servletContext, String path, Class<Servlet> servlet) {
    servletContext.addServlet(servlet, path)
  }

  // FIXME: Add authentication tests back in...
//  static setupAuthentication(Server jettyServer, ServletContextHandler servletContext) {
//    ConstraintSecurityHandler authConfig = new ConstraintSecurityHandler()
//
//    Constraint constraint = new Constraint()
//    constraint.setName("auth")
//    constraint.setAuthenticate(true)
//    constraint.setRoles("role")
//
//    ConstraintMapping mapping = new ConstraintMapping()
//    mapping.setPathSpec("/auth/*")
//    mapping.setConstraint(constraint)
//
//    authConfig.setConstraintMappings(mapping)
//    authConfig.setAuthenticator(new BasicAuthenticator())
//
//    LoginService loginService = new HashLoginService("TestRealm",
//      "src/test/resources/realm.properties")
//    authConfig.setLoginService(loginService)
//    jettyServer.addBean(loginService)
//
//    servletContext.setSecurityHandler(authConfig)
//  }
}

class JettyServlet3TestSync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }
}

class JettyServlet3TestAsync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }
}

class JettyServlet3TestFakeAsync extends JettyServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.FakeAsync
  }
}


class JettyServlet3TestDispatchImmediate extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  protected void setupServlets(ServletContextHandler context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

class JettyServlet3TestDispatchAsync extends JettyDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }

  @Override
  protected void setupServlets(ServletContextHandler context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

abstract class JettyDispatchTest extends JettyServlet3Test {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port/$context/dispatch/")
  }

  @Override
  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.InMemoryExporterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_EXPORTER.waitForTraces(size * 3) // (test, dispatch, and servlet/controller traces
    // TEST_EXPORTER traces is a CopyOnWriteArrayList, which doesn't support remove() on iterator
    def toRemove = TEST_EXPORTER.traces.findAll {
      it.size() == 1 && it.get(0).name == "TEST_SPAN"
    }
    assert toRemove.size() == size
    toRemove.each {
      assertTrace(it, 1) {
        basicSpan(it, 0, "TEST_SPAN", "ServerEntry")
      }
    }
    TEST_EXPORTER.traces.removeAll(toRemove)

    // Validate dispatch trace
    def dispatchTraces = TEST_EXPORTER.traces.findAll {
      it.size() == 1 && it.get(0).attributes.attributeMapMap[AttributeNames.RESOURCE_NAME].stringValue.contains("/dispatch/")
    }
    assert dispatchTraces.size() == size
    dispatchTraces.each { List<Span> dispatchTrace ->
      assertTrace(dispatchTrace, 1) {
        def endpoint = lastRequest
        span(0) {
          spanKind Span.SpanKind.SERVER
          spanName expectedOperationName()
          // we can't reliably assert parent or child relationship here since both are tested.
          tags {
            "$AttributeNames.SPAN_TYPE" DDSpanTypes.HTTP_SERVER

            "servlet.context" "/$context"
            "servlet.dispatch" endpoint.path
            "span.origin.type" {
              it == TestServlet3.DispatchImmediate.name || it == TestServlet3.DispatchAsync.name || it == ApplicationFilterChain.name
            }

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
            "$AttributeNames.HTTP_METHOD" "GET"
          }
        }
      }
      // Make sure that the trace has a span with the dispatchTrace as a parent.
      assert TEST_EXPORTER.traces.any { it.any { it.parentSpanId == dispatchTrace[0].spanId } }
    }
  }
}
