import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "api-platform-outbound-soap"

val silencerVersion = "1.7.9"

scalaVersion := "2.13.12"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    majorVersion                     := 0,
    PlayKeys.playDefaultPort         := 6703,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    dependencyOverrides              ++= AppDependencies.axiomOverrides,
  )
  .settings(ScoverageSettings())
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(scalafixConfigSettings(IntegrationTest))
  .settings(
    IntegrationTest / unmanagedResourceDirectories += baseDirectory.value / "test" / "resources"
  )
  .settings(
    Compile / unmanagedResourceDirectories += baseDirectory.value / "app" / "resources"
  )
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:cat=unused&src=views/.*\\.scala:s",
      "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:cat=unused&src=.*Routes\\.scala:s",
      "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s"
    )
  )

commands ++= Seq(
  Command.command("run-all-tests") { state => "test" :: "it:test" :: state },

  Command.command("clean-and-test") { state => "clean" :: "compile" :: "run-all-tests" :: state },

  // Coverage does not need compile !
  Command.command("pre-commit") { state => "clean" :: "scalafmtAll" :: "scalafixAll" :: "coverage" :: "run-all-tests" :: "coverageOff" :: "coverageAggregate" :: state }
)
