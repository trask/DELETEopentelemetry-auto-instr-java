import datadog.trace.agent.tooling.AttributeNames
import datadog.trace.agent.tooling.DDSpanTypes
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.instrumentation.servlet2.Servlet2Decorator
import io.opentelemetry.proto.trace.v1.Span
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.http.HttpServletRequest

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*

class JettyServlet2Test extends HttpServerTest<Server, Servlet2Decorator> {

  private static final CONTEXT = "ctx"

  @Override
  Server startServer(int port) {
    def jettyServer = new Server(port)
    jettyServer.connectors.each { it.resolveNames = true } // get localhost instead of 127.0.0.1
    ServletContextHandler servletContext = new ServletContextHandler(null, "/$CONTEXT")
    servletContext.errorHandler = new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    }

    // FIXME: Add tests for security/authentication.
//    ConstraintSecurityHandler security = setupAuthentication(jettyServer)
//    servletContext.setSecurityHandler(security)

    servletContext.addServlet(TestServlet2.Sync, SUCCESS.path)
    servletContext.addServlet(TestServlet2.Sync, ERROR.path)
    servletContext.addServlet(TestServlet2.Sync, EXCEPTION.path)
    servletContext.addServlet(TestServlet2.Sync, REDIRECT.path)
    servletContext.addServlet(TestServlet2.Sync, AUTH_REQUIRED.path)

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
  URI buildAddress() {
    return new URI("http://localhost:$port/$CONTEXT/")
  }

  @Override
  Servlet2Decorator decorator() {
    return Servlet2Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  @Override
  boolean testNotFound() {
    false
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      spanKind Span.SpanKind.SERVER
      spanName expectedOperationName()
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$AttributeNames.SPAN_TYPE" DDSpanTypes.HTTP_SERVER

        "servlet.context" "/$CONTEXT"
        "span.origin.type" TestServlet2.Sync.name

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
        // No peer port
        "$AttributeNames.PEER_HOST_IPV4" "127.0.0.1"
        "$AttributeNames.HTTP_METHOD" method
      }
    }
  }

  /**
   * Setup simple authentication for tests
   * <p>
   *     requests to {@code /auth/*} need login 'user' and password 'password'
   * <p>
   *     For details @see <a href="http://www.eclipse.org/jetty/documentation/9.3.x/embedded-examples.html">http://www.eclipse.org/jetty/documentation/9.3.x/embedded-examples.html</a>
   *
   * @param jettyServer server to attach login service
   * @return SecurityHandler that can be assigned to servlet
   */
//  private ConstraintSecurityHandler setupAuthentication(Server jettyServer) {
//    ConstraintSecurityHandler security = new ConstraintSecurityHandler()
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
//    security.setConstraintMappings(mapping)
//    security.setAuthenticator(new BasicAuthenticator())
//
//    LoginService loginService = new HashLoginService("TestRealm",
//      "src/test/resources/realm.properties")
//    security.setLoginService(loginService)
//    jettyServer.addBean(loginService)
//
//    security
//  }
}
