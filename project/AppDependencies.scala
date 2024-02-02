import play.sbt.PlayImport.caffeine
import sbt._

object AppDependencies {
  val bootstrapPlayVersion = "8.4.0"
  val mongoVersion         = "1.7.0"

  val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-backend-play-30"   % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-30"          % mongoVersion,
    "org.apache.pekko"   %% "pekko-connectors-mongodb"    % "1.0.2",
    "org.apache.axis2"   %  "axis2-kernel"                % "1.8.0",
    "org.apache.wss4j"   %  "wss4j-ws-security-dom"       % "2.4.1",
    caffeine
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapPlayVersion % "test, it",
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.29"            % "test, it",
    "org.xmlunit"            %  "xmlunit-core"            % "2.9.0"              % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % mongoVersion         % "it"
  )

  val axiomVersion = "1.4.0"
  val axiomOverrides = Seq(
    "org.apache.ws.commons.axiom" % "axiom" % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-api" % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-impl" % axiomVersion,
  )
}
