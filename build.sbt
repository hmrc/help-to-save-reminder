
val appName = "help-to-save-reminder"

lazy val microservice = {
  Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"
  )
  .settings(scalaVersion := "3.3.6")
  .settings(PlayKeys.playDefaultPort := 7008)
  .settings(CodeCoverageSettings.settings *)
  .settings(scalafmtOnCompile := true)
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
}
