import com.google.common.io.Files
import datadog.trace.agent.test.asserts.InMemoryExporterAssert
import datadog.trace.agent.tooling.AttributeNames
import datadog.trace.agent.tooling.DDSpanTypes
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.proto.trace.v1.Span
import org.apache.catalina.Context
import org.apache.catalina.connector.Request
import org.apache.catalina.connector.Response
import org.apache.catalina.core.ApplicationFilterChain
import org.apache.catalina.core.StandardHost
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.valves.ErrorReportValve
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType

import javax.servlet.Servlet

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan

abstract class TomcatServlet3Test extends AbstractServlet3Test<Tomcat, Context> {

  @Override
  Tomcat startServer(int port) {
    def tomcatServer = new Tomcat()

    def baseDir = Files.createTempDir()
    baseDir.deleteOnExit()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    tomcatServer.setPort(port)
    tomcatServer.getConnector().enableLookups = true // get localhost instead of 127.0.0.1

    final File applicationDir = new File(baseDir, "/webapps/ROOT")
    if (!applicationDir.exists()) {
      applicationDir.mkdirs()
      applicationDir.deleteOnExit()
    }
    Context servletContext = tomcatServer.addWebapp("/$context", applicationDir.getAbsolutePath())
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter(new JarScanFilter() {
      @Override
      boolean check(JarScanType jarScanType, String jarName) {
        return false
      }
    })

//    setupAuthentication(tomcatServer, servletContext)
    setupServlets(servletContext)

    (tomcatServer.host as StandardHost).errorReportValveClass = ErrorHandlerValve.name

    tomcatServer.start()

    return tomcatServer
  }

  @Override
  void stopServer(Tomcat server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContext() {
    return "tomcat-context"
  }

  @Override
  void addServlet(Context servletContext, String path, Class<Servlet> servlet) {
    String name = UUID.randomUUID()
    Tomcat.addServlet(servletContext, name, servlet.newInstance())
    servletContext.addServletMappingDecoded(path, name)
  }

  // FIXME: Add authentication tests back in...
//  private setupAuthentication(Tomcat server, Context servletContext) {
//    // Login Config
//    LoginConfig authConfig = new LoginConfig()
//    authConfig.setAuthMethod("BASIC")
//
//    // adding constraint with role "test"
//    SecurityConstraint constraint = new SecurityConstraint()
//    constraint.addAuthRole("role")
//
//    // add constraint to a collection with pattern /second
//    SecurityCollection collection = new SecurityCollection()
//    collection.addPattern("/auth/*")
//    constraint.addCollection(collection)
//
//    servletContext.setLoginConfig(authConfig)
//    // does the context need a auth role too?
//    servletContext.addSecurityRole("role")
//    servletContext.addConstraint(constraint)
//
//    // add tomcat users to realm
//    MemoryRealm realm = new MemoryRealm() {
//      protected void startInternal() {
//        credentialHandler = new MessageDigestCredentialHandler()
//        setState(LifecycleState.STARTING)
//      }
//    }
//    realm.addUser(user, pass, "role")
//    server.getEngine().setRealm(realm)
//
//    servletContext.setLoginConfig(authConfig)
//  }
}

class ErrorHandlerValve extends ErrorReportValve {
  @Override
  protected void report(Request request, Response response, Throwable t) {
    if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
      return
    }
    try {
      response.writer.print(t ? t.cause.message : response.message)
    } catch (IOException e) {
      e.printStackTrace()
    }
  }
}

class TomcatServlet3TestSync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }
}

class TomcatServlet3TestAsync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }
}

class TomcatServlet3TestFakeAsync extends TomcatServlet3Test {

  @Override
  Class<Servlet> servlet() {
    TestServlet3.FakeAsync
  }
}

class TomcatServlet3TestDispatchImmediate extends TomcatDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  protected void setupServlets(Context context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet3.DispatchImmediate)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

class TomcatServlet3TestDispatchAsync extends TomcatDispatchTest {
  @Override
  Class<Servlet> servlet() {
    TestServlet3.Async
  }

  @Override
  protected void setupServlets(Context context) {
    super.setupServlets(context)

    addServlet(context, "/dispatch" + SUCCESS.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + ERROR.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + EXCEPTION.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + REDIRECT.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch" + AUTH_REQUIRED.path, TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
  }
}

abstract class TomcatDispatchTest extends TomcatServlet3Test {
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
    if (lastRequest == NOT_FOUND) {
      TEST_EXPORTER.waitForTraces(size * 2) // (test and servlet/controller traces
    } else {
      TEST_EXPORTER.waitForTraces(size * 3) // (test, dispatch, and servlet/controller traces
    }
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

    if (lastRequest == NOT_FOUND) {
      // Tomcat won't "dispatch" an unregistered url
      assertTraces(size, spec)
      return
    }

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
      assert TEST_EXPORTER.traces.any { it.any { it.parentId == dispatchTrace[0].spanId } }
    }
  }
}
