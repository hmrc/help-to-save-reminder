import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "help-to-save-reminder"

lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages := "<empty>;app.*;test.*;config.*;metrics.*;testOnlyDoNotUseInAppConf.*;views.html.*;prod.*;uk.gov.hmrc.helptosavereminder.controllers.test.*;uk.gov.hmrc.helptosavereminder.models.test.*;uk.gov.hmrc.helptosavereminder.services.test.*",
  ScoverageKeys.coverageMinimum := 80,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(scalaVersion := "2.12.11")
  .settings(PlayKeys.playDefaultPort := 7008)
  .settings(scoverageSettings)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalafmtOnCompile := true)
