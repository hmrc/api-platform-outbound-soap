import play.sbt.PlayImport.caffeine
import sbt.*

object AppDependencies {
  val bootstrapPlayVersion = "10.7.0"
  val mongoVersion         = "2.12.0"

  val compile              = Seq(
    "uk.gov.hmrc"               %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-30"        % mongoVersion,
    "org.apache.axis2"           % "axis2-kernel"              % "2.0.0",
    "org.apache.james"           % "apache-mime4j-core"        % "0.8.11",
    "org.apache.wss4j"           % "wss4j-ws-security-dom"     % "3.0.5",
    "org.apache.velocity"        % "velocity-engine-core"      % "2.4.1",
    // explicitly included as velocity-engine-core uses a vulnerable version
    "org.apache.commons"         % "commons-lang3"             % "3.20.0",
    "commons-io"                 % "commons-io"                % "2.18.0",
    "com.sun.activation"         % "javax.activation"          % "1.2.0",
    caffeine
  )

  val test                 = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion,
    "org.mockito"       %% "mockito-scala-scalatest" % "1.17.30",
    "org.xmlunit"        % "xmlunit-core"            % "2.9.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoVersion
  ).map(_ % "test")
  val axiomVersion         = "1.4.0"
  val axiomOverrides       = Seq(
    "org.apache.ws.commons.axiom" % "axiom"      % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-api"  % axiomVersion,
    "org.apache.ws.commons.axiom" % "axiom-impl" % axiomVersion
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
