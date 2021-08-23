import play.core.PlayVersion.current
import play.sbt.PlayImport.caffeine
import sbt._

object AppDependencies {
  val akkaVersion = "2.6.14"
  val bootstrapPlayVersion = "5.10.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28" % "0.52.0",
    "uk.gov.hmrc" %% "mongo-lock" % "7.0.0-play-28",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-28",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2",
    "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "3.0.1",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf-v3" % akkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
    "org.reactivemongo" %% "reactivemongo-akkastream" % "0.20.13",
    "org.apache.axis2" % "axis2-kernel" % "1.7.9",
    "org.apache.wss4j" % "wss4j-ws-security-dom" % "2.3.0",
    "com.beachape" %% "enumeratum-play-json" % "1.6.0",
    "com.typesafe.play" %% "play-json-joda" % "2.7.1",
    "org.typelevel"     %% "cats-core"                  % "2.0.0",
    caffeine
  )

  val test = Seq(
    "org.scalamock"        %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "uk.gov.hmrc" %% "bootstrap-test-play-28" % bootstrapPlayVersion % "test, it",
    "org.scalatest" %% "scalatest" % "3.1.2" % "test, it",
    "org.mockito" %% "mockito-scala-scalatest" % "1.14.4" % "test, it",
    "com.typesafe.play" %% "play-test" % current % "test, it",
    "com.vladsch.flexmark" %  "flexmark-all" % "0.35.10" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % "test, it",
    "com.github.tomakehurst" % "wiremock" % "2.25.1" % "it",
    "org.xmlunit" % "xmlunit-core" % "2.8.1" % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.52.0" % "it"
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
