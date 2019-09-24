package datadog.trace.agent.test.base

import datadog.trace.agent.tooling.AttributeNames
import datadog.trace.agent.tooling.DDSpanTypes
import datadog.trace.agent.decorator.HttpServerDecorator
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.InMemoryExporterAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.proto.trace.v1.Span
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicBoolean

import static datadog.trace.agent.test.asserts.TraceAssert.assertTrace
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

@Unroll
abstract class HttpServerTest<SERVER, DECORATOR extends HttpServerDecorator> extends AgentTestRunner {

  @Shared
  SERVER server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  URI address = buildAddress()

  URI buildAddress() {
    return new URI("http://localhost:$port/")
  }

  @Shared
  DECORATOR serverDecorator = decorator()

  def setupSpec() {
    server = startServer(port)
    println getClass().name + " http server started at: http://localhost:$port/"
  }

  abstract SERVER startServer(int port)

  def cleanupSpec() {
    if (server == null) {
      println getClass().name + " can't stop null server"
      return
    }
    stopServer(server)
    server = null
    println getClass().name + " http server stopped at: http://localhost:$port/"
  }

  abstract void stopServer(SERVER server)

  abstract DECORATOR decorator()

  abstract String expectedOperationName()

  boolean hasHandlerSpan() {
    false
  }

  // Return the handler span's name
  String reorderHandlerSpan() {
    null
  }

  boolean reorderControllerSpan() {
    false
  }

  boolean redirectHasBody() {
    false
  }

  boolean testNotFound() {
    true
  }

  boolean testExceptionBody() {
    true
  }

  enum ServerEndpoint {
    SUCCESS("success", 200, "success"),
    REDIRECT("redirect", 302, "/redirected"),
    ERROR("error-status", 500, "controller error"), // "error" is a special path for some frameworks
    EXCEPTION("exception", 500, "controller exception"),
    NOT_FOUND("notFound", 404, "not found"),

    // TODO: add tests for the following cases:
    PATH_PARAM("path/123/param", 200, "123"),
    AUTH_REQUIRED("authRequired", 200, null),

    private final String path
    final int status
    final String body
    final Boolean errored

    ServerEndpoint(String path, int status, String body) {
      this.path = path
      this.status = status
      this.body = body
      this.errored = status >= 500
    }

    String getPath() {
      return "/$path"
    }

    String rawPath() {
      return path
    }

    URI resolve(URI address) {
      return address.resolve(path)
    }

    private static final Map<String, ServerEndpoint> PATH_MAP = values().collectEntries { [it.path, it] }

    static ServerEndpoint forPath(String path) {
      return PATH_MAP.get(path)
    }
  }

  Request.Builder request(ServerEndpoint uri, String method, String body) {
    return new Request.Builder()
      .url(HttpUrl.get(uri.resolve(address)))
      .method(method, body)
  }

  static <T> T controller(ServerEndpoint endpoint, Closure<T> closure) {
    // TODO trask
    // assert ((TraceScope) GlobalTracer.get().scopeManager().active()).asyncPropagating
    if (endpoint == NOT_FOUND) {
      return closure()
    }
    return runUnderTrace("controller", closure)
  }

  def "test success with #count requests"() {
    setup:
    def request = request(SUCCESS, method, body).build()
    List<Response> responses = (1..count).collect {
      return client.newCall(request).execute()
    }

    expect:
    responses.each { response ->
      assert response.code() == SUCCESS.status
      assert response.body().string() == SUCCESS.body
    }

    and:
    cleanAndAssertTraces(count) {
      (1..count).eachWithIndex { val, i ->
        if (hasHandlerSpan()) {
          trace(i, 3) {
            serverSpan(it, 0)
            handlerSpan(it, 1, span(0))
            controllerSpan(it, 2, span(1))
          }
        } else {
          trace(i, 2) {
            serverSpan(it, 0)
            controllerSpan(it, 1, span(0))
          }
        }
      }
    }

    where:
    method = "GET"
    body = null
    count << [1, 4, 50] // make multiple requests.
  }

  def "test success with parent"() {
    setup:
    def traceId = "00000000000000000000000000000123"
    def spanId = "0000000000000456"
    def request = request(SUCCESS, method, body)
      .header("traceparent", "00-" + traceId + "-" + spanId + "-00")
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, traceId, spanId)
          handlerSpan(it, 1, span(0))
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, traceId, spanId)
          controllerSpan(it, 1, span(0))
        }
      }
    }

    where:
    method = "GET"
    body = null
  }

  def "test redirect"() {
    setup:
    def request = request(REDIRECT, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == REDIRECT.status
    response.header("location") == REDIRECT.body ||
      response.header("location") == "${address.resolve(REDIRECT.body)}"
    response.body().contentLength() < 1 || redirectHasBody()

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, method, REDIRECT)
          handlerSpan(it, 1, span(0), REDIRECT)
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, REDIRECT)
          controllerSpan(it, 1, span(0))
        }
      }
    }

    where:
    method = "GET"
    body = null
  }

  def "test error"() {
    setup:
    def request = request(ERROR, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == ERROR.status
    response.body().string() == ERROR.body

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, method, ERROR)
          handlerSpan(it, 1, span(0), ERROR)
          controllerSpan(it, 2, span(1))
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, ERROR)
          controllerSpan(it, 1, span(0))
        }
      }
    }

    where:
    method = "GET"
    body = null
  }

  def "test exception"() {
    setup:
    def request = request(EXCEPTION, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == EXCEPTION.status
    if (testExceptionBody()) {
      assert response.body().string() == EXCEPTION.body
    }

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 3) {
          serverSpan(it, 0, null, null, method, EXCEPTION)
          handlerSpan(it, 1, span(0), EXCEPTION)
          controllerSpan(it, 2, span(1), EXCEPTION.body)
        }
      } else {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, EXCEPTION)
          controllerSpan(it, 1, span(0), EXCEPTION.body)
        }
      }
    }

    where:
    method = "GET"
    body = null
  }

  def "test notFound"() {
    setup:
    assumeTrue(testNotFound())
    def request = request(NOT_FOUND, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == NOT_FOUND.status

    and:
    cleanAndAssertTraces(1) {
      if (hasHandlerSpan()) {
        trace(0, 2) {
          serverSpan(it, 0, null, null, method, NOT_FOUND)
          handlerSpan(it, 1, span(0), NOT_FOUND)
        }
      } else {
        trace(0, 1) {
          serverSpan(it, 0, null, null, method, NOT_FOUND)
        }
      }
    }

    where:
    method = "GET"
    body = null
  }

  //FIXME: add tests for POST with large/chunked data

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.InMemoryExporterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_EXPORTER.waitForTraces(size * 2)
    def toRemove = TEST_EXPORTER.traces.findAll {
      it.size() == 1 && it.get(0).name == "TEST_SPAN"
    }
    toRemove.each {
      assertTrace(it, 1) {
        basicSpan(it, 0, "TEST_SPAN", "ServerEntry")
      }
    }
    assert toRemove.size() == size
    TEST_EXPORTER.traces.removeAll(toRemove)

    if (reorderHandlerSpan()) {
      TEST_EXPORTER.traces.each {
        def controllerSpan = it.find {
          it.name == reorderHandlerSpan()
        }
        if (controllerSpan) {
          it.remove(controllerSpan)
          it.add(controllerSpan)
        }
      }
    }

    if (reorderControllerSpan() || reorderHandlerSpan()) {
      // Some frameworks close the handler span before the controller returns, so we need to manually reorder it.
      TEST_EXPORTER.traces.each {
        def controllerSpan = it.find {
          it.name == "controller"
        }
        if (controllerSpan) {
          it.remove(controllerSpan)
          it.add(controllerSpan)
        }
      }
    }

    assertTraces(size, spec)
  }

  void controllerSpan(TraceAssert trace, int index, Object parent, String errorMessage = null) {
    trace.span(index) {
      spanKind Span.SpanKind.INTERNAL
      spanName "controller"
      childOf(parent as Span)
      tags {
        defaultTags()
        if (errorMessage) {
          errorTags(Exception, errorMessage)
        }
      }
    }
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("handlerSpan not implemented in " + getClass().name)
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

        defaultTags(true)
        "$AttributeNames.COMPONENT" serverDecorator.component()
        if (endpoint.errored) {
          "$AttributeNames.ERROR" true
        }
        "$AttributeNames.HTTP_STATUS" endpoint.status
        "$AttributeNames.HTTP_URL" "${endpoint.resolve(address)}"
//        if (tagQueryString) {
//          "$DDTags.HTTP_QUERY" uri.query
//          "$DDTags.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
//        }
        "$AttributeNames.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$AttributeNames.PEER_PORT" Integer
        "$AttributeNames.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$AttributeNames.HTTP_METHOD" method
      }
    }
  }

  public static final AtomicBoolean ENABLE_TEST_ADVICE = new AtomicBoolean(false)

  def setup() {
    ENABLE_TEST_ADVICE.set(true)
  }

  def cleanup() {
    ENABLE_TEST_ADVICE.set(false)
  }
}
