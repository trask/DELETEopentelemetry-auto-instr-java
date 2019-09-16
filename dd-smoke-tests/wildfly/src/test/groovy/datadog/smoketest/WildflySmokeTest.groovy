package datadog.smoketest

import datadog.trace.agent.test.utils.PortUtils
import okhttp3.Request
import spock.lang.Shared

class WildflySmokeTest extends AbstractServerSmokeTest {

  @Shared
  File wildflyDirectory = new File(System.getProperty("datadog.smoketest.wildflyDir"))
  @Shared
  int httpsPort = PortUtils.randomOpenPort()
  @Shared
  int managementPort = PortUtils.randomOpenPort()

  @Override
  ProcessBuilder createProcessBuilder() {
    String ext = System.getProperty("os.name").startsWith("Windows") ? "bat" : "sh"
    ProcessBuilder processBuilder =
      new ProcessBuilder("${wildflyDirectory}/bin/standalone." + ext)
    processBuilder.directory(wildflyDirectory)
    processBuilder.environment().put("JAVA_OPTS",
      defaultJavaProperties.join(" ")
        + " -Djboss.http.port=${httpPort} -Djboss.https.port=${httpsPort}"
        + " -Djboss.management.http.port=${managementPort}")
    return processBuilder
  }

  def cleanupSpec() {
    String ext = System.getProperty("os.name").startsWith("Windows") ? "bat" : "sh"
    ProcessBuilder processBuilder = new ProcessBuilder(
      "${wildflyDirectory}/bin/jboss-cli." + ext,
      "--connect",
      "--controller=localhost:${managementPort}",
      "command=:shutdown")
    processBuilder.directory(wildflyDirectory)
    Process process = processBuilder.start()
    process.getOutputStream().close() // otherwise .bat file waits at end with "Press any key to continue . . ."
    process.waitFor()
  }

  def "default home page #n th time"() {
    setup:
    String url = "http://localhost:$httpPort/"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr != null
    responseBodyStr.contains("Your WildFly instance is running.")
    response.body().contentType().toString().contains("text/html")
    response.code() == 200

    where:
    n << (1..200)
  }
}
