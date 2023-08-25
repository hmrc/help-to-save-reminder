val appName = "help-to-save-reminder"

lazy val microservice = {
  Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .settings(
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test()
  )
  .settings(scalaVersion := "2.13.11")
  .settings(PlayKeys.playDefaultPort := 7008)
  .settings(CodeCoverageSettings.settings *)
  .settings(scalafmtOnCompile := true)
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
}

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
