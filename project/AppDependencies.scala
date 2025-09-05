import play.sbt.PlayImport.caffeine
import sbt.*

object AppDependencies {
  val bootstrapPlayVersion = "9.19.0"
  val mongoVersion         = "2.7.0"
  val compile = Seq(
    "uk.gov.hmrc"         %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-play-30"        % mongoVersion,
    "org.apache.pekko"    %% "pekko-connectors-mongodb"  % "1.0.2",
    "org.apache.axis2"    % "axis2-kernel"               % "1.8.2",
    "org.apache.james"    % "apache-mime4j-core"         % "0.8.11",
    "org.apache.wss4j"    % "wss4j-ws-security-dom"      % "3.0.4",
    "org.apache.velocity" % "velocity-engine-core"       % "2.4.1",
    "commons-io"          % "commons-io"                 % "2.18.0",
    "com.sun.activation"  % "javax.activation"           % "1.2.0",
    caffeine
  )
  val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion,
    "org.mockito"       %% "mockito-scala-scalatest" % "1.17.30",
    "org.xmlunit"        % "xmlunit-core"            % "2.9.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoVersion
  ).map(_ % "test")
  val axiomVersion   = "1.4.0"
  val axiomOverrides = Seq(
    "org.apache.ws.commons.axiom" % "axiom"      % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-api"  % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-impl" % axiomVersion
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
