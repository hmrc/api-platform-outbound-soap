import scoverage.ScoverageKeys._

object ScoverageSettings {

  def apply() = Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    coverageMinimumStmtTotal   := 90,
    coverageMinimumBranchTotal := 90,
    coverageFailOnMinimum      := true,
    coverageHighlighting       := true,
    coverageExcludedPackages   := Seq(
      "<empty>",
      "prod.*",
      "testOnlyDoNotUseInAppConf.*",
      "app.*",
      "app",
      ".*Reverse.*",
      "Reverse.*",
      "Routes.*",
      "router\\.*",
      ".*definition.*",
      "uk.gov.hmrc.BuildInfo.*"
    ).mkString(";")
  )
}
