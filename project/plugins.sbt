resolvers ++= Seq(
  Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns),
  "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/",
  "third-party-maven-releases" at "https://artefacts.tax.service.gov.uk/artifactory/third-party-maven-releases/",
  "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2")

resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("uk.gov.hmrc"       %  "sbt-auto-build"        % "3.18.0")
addSbtPlugin("uk.gov.hmrc"       %  "sbt-distributables"    % "2.4.0")
addSbtPlugin("com.typesafe.play" %  "sbt-plugin"            % "2.8.18")
addSbtPlugin("org.scoverage"     %  "sbt-scoverage"         % "2.0.9")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scalameta"     %  "sbt-scalafmt"          % "2.5.2")
addSbtPlugin("ch.epfl.scala"     %  "sbt-scalafix"          % "0.11.1")

addDependencyTreePlugin

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always