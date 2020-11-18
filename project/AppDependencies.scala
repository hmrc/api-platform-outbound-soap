import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "3.0.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-27",
    "org.apache.cxf" % "cxf-tools-wsdlto-core" % "3.4.1"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-27" % "3.0.0" % "test, it",
    "org.scalatest" %% "scalatest" % "3.1.2" % "test, it",
    "org.mockito" %% "mockito-scala-scalatest" % "1.14.4" % "test, it",
    "com.typesafe.play" %% "play-test" % current % "test, it",
    "com.vladsch.flexmark" %  "flexmark-all" % "0.35.10" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test, it",
    "com.github.tomakehurst" % "wiremock-jre8" % "2.25.1" % "it"
  )
}
