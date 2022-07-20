import play.core.PlayVersion.current
import play.sbt.PlayImport.caffeine
import sbt._

object AppDependencies {
  val akkaVersion = "2.6.19"
  val bootstrapPlayVersion = "6.3.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28" % "0.65.0",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2",
    "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "3.0.4",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf-v3" % akkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
    "org.apache.axis2" % "axis2-kernel" % "1.8.0",
    "org.apache.wss4j" % "wss4j-ws-security-dom" % "2.4.1",
    "com.beachape" %% "enumeratum-play-json" % "1.7.0",
    caffeine
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-28" % bootstrapPlayVersion % "test, it",
    "org.scalatest" %% "scalatest" % "3.2.12" % "test, it",
    "org.mockito" %% "mockito-scala-scalatest" % "1.17.7" % "test, it",
    "com.typesafe.play" %% "play-test" % current % "test, it",
    "com.vladsch.flexmark" %  "flexmark-all" % "0.64.0" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % "test, it",
    "org.xmlunit" % "xmlunit-core" % "2.9.0" % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.52.0" % "it",
  )

  val axiomVersion = "1.4.0"
  val axiomOverrides = Seq(
    "org.apache.ws.commons.axiom" % "axiom" % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-api" % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-impl" % axiomVersion,
  )
}
