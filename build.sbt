import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import wartremover.{Wart, Warts, wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save-reminder"

lazy val wartRemoverSettings = {
  // list of warts here: http://www.wartremover.org/doc/warts.html
  val excludedWarts = Seq(
    Wart.DefaultArguments,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.LeakingSealed,
    Wart.Nothing,
    Wart.Overloading,
    Wart.ToString,
    Wart.Var
  )

  Seq(
    wartremoverErrors in (Compile, compile) ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // incompatible with a lot of WordSpec
    wartremoverErrors in (Test, compile) --= Seq(
      Wart.Any,
      Wart.Equals,
      Wart.Null,
      Wart.NonUnitStatements,
      Wart.PublicInference),
    wartremoverExcluded in (Compile, compile) ++=
      routes.in(Compile).value ++
        (baseDirectory.value ** "*.sc").get ++
        (baseDirectory.value ** "ProcessingSupervisor.scala").get ++
        (baseDirectory.value ** "EmailSenderActor.scala").get ++
        (baseDirectory.value ** "HtsUserUpdateActor.scala").get ++
        (baseDirectory.value ** "EmailCallbackController.scala").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala") ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosavereminder" / "config").get,
    wartremoverExcluded in (Test, compile) ++=
      (baseDirectory.value ** "HtsReminderRepositorySpec.scala").get ++
        (baseDirectory.value ** "EmailCallbackControllerSpec.scala").get ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosavereminder").get
  )
}
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
  .settings(wartRemoverSettings)
  .settings(scoverageSettings)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalafmtOnCompile := true)

import play.sbt.routes.RoutesKeys
RoutesKeys.routesImport := Seq.empty
