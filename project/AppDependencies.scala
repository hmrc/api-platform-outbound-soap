import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "3.0.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-27",
    "org.apache.axis2" % "axis2-kernel" % "1.7.9",
    "org.apache.wss4j" % "wss4j-ws-security-dom" % "2.3.0",
    "com.beachape" %% "enumeratum-play-json" % "1.6.0",
    "com.typesafe.play" %% "play-json-joda" % "2.7.1"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-27" % "3.0.0" % "test, it",
    "org.scalatest" %% "scalatest" % "3.1.2" % "test, it",
    "org.mockito" %% "mockito-scala-scalatest" % "1.14.4" % "test, it",
    "com.typesafe.play" %% "play-test" % current % "test, it",
    "com.vladsch.flexmark" %  "flexmark-all" % "0.35.10" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test, it",
    "com.github.tomakehurst" % "wiremock" % "2.25.1" % "it",
    "org.xmlunit" % "xmlunit-core" % "2.8.1" % "test, it",
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26" % "it"
  )

  val jettyVersion = "9.2.24.v20180105"
  val jettyOverrides = Seq(
    "org.eclipse.jetty" % "jetty-server" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-security" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-continuation" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-xml" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-client" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-http" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-io" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty" % "jetty-util" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty.websocket" % "websocket-api" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty.websocket" % "websocket-common" % jettyVersion % IntegrationTest,
    "org.eclipse.jetty.websocket" % "websocket-client" % jettyVersion % IntegrationTest
  )
}
