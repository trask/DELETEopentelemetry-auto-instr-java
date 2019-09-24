package datadog.trace.agent.tooling;

public class AttributeNames {

  // names from io.opentracing.tag.Tags

  public static final String COMPONENT = "component";
  public static final String ERROR = "error";

  public static final String HTTP_URL = "http.url";
  public static final String HTTP_STATUS = "http.status_code";
  public static final String HTTP_METHOD = "http.method";

  public static final String DB_TYPE = "db.type";
  public static final String DB_INSTANCE = "db.instance";
  public static final String DB_USER = "db.user";
  public static final String DB_STATEMENT = "db.statement";

  // names from io.opentracing.log.Fields

  public static final String ERROR_OBJECT = "error.object";

  public static final String PEER_HOSTNAME = "peer.hostname";
  public static final String PEER_PORT = "peer.port";
  public static final String PEER_HOST_IPV4 = "peer.ipv4";
  public static final String PEER_HOST_IPV6 = "peer.ipv6";

  // names from datadog.trace.api.DDTags

  public static final String SPAN_TYPE = "span.type";
  public static final String SERVICE_NAME = "service.name";
  public static final String RESOURCE_NAME = "resource.name";
  public static final String THREAD_NAME = "thread.name";
  public static final String THREAD_ID = "thread.id";

  public static final String HTTP_QUERY = "http.query.string";
  public static final String HTTP_FRAGMENT = "http.fragment.string";

  public static final String USER_NAME = "user.principal";

  public static final String ERROR_MSG = "error.msg"; // string representing the error message
  public static final String ERROR_TYPE = "error.type"; // string representing the type of the error
  public static final String ERROR_STACK = "error.stack"; // human readable version of the stack

  public static final String ANALYTICS_SAMPLE_RATE = "_dd1.sr.eausr";
}
