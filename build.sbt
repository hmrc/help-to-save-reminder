import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import wartremover.{Wart, Warts}
import wartremover.WartRemover.autoImport.{wartremoverErrors,wartremoverExcluded}

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
    Compile / compile / wartremoverErrors ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // incompatible with a lot of WordSpec
    Test / compile / wartremover.WartRemover.autoImport.wartremoverErrors --= Seq(
      Wart.Any,
      Wart.Equals,
      Wart.Null,
      Wart.NonUnitStatements,
      Wart.PublicInference),
    wartremover.WartRemover.autoImport.wartremoverExcluded  ++=
      (Compile / routes).value ++
        (baseDirectory.value ** "*.sc").get ++
        (baseDirectory.value ** "ProcessingSupervisor.scala").get ++
        (baseDirectory.value ** "EmailSenderActor.scala").get ++
        (baseDirectory.value ** "HtsUserUpdateActor.scala").get ++
        (baseDirectory.value ** "EmailCallbackController.scala").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala") ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosavereminder" / "config").get,
    wartremover.WartRemover.autoImport.wartremoverExcluded ++=
      (Test / routes).value ++
        (baseDirectory.value ** "AuthSupport.scala").get ++
        (baseDirectory.value ** "*Spec.scala").get ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosavereminder").get,
    wartremoverExcluded ++= (Compile / routes).value
  )
}

lazy val microservice = {
  Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(Compile / doc / sources := Seq.empty)
  .settings(integrationTestSettings(): _*)
  .settings(scalaVersion := "2.12.13")
  .settings(PlayKeys.playDefaultPort := 7008)
  .settings(wartRemoverSettings)
  .settings(CodeCoverageSettings.settings *)
  .settings(scalafmtOnCompile := true)
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(scalacOptions += "-P:wartremover:only-warn-traverser:org.wartremover.warts.Unsafe")
}
import play.sbt.routes.RoutesKeys
RoutesKeys.routesImport := Seq.empty
