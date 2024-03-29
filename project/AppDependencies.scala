import sbt.*

object AppDependencies {

  private val playVersion = "play-28"
  private val hmrcBootstrapVersion = "7.11.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"        % s"0.70.0",
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc"       %% "domain"                          % s"8.3.0-$playVersion",
    "org.typelevel"     %% "cats-core"                       % "2.6.0",
    "com.enragedginger" %% "akka-quartz-scheduler"           % "1.9.0-akka-2.6.x"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % hmrcBootstrapVersion % scope,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.70.0"             % scope,
    "org.mockito"       %% "mockito-scala"           % "1.17.12"            % scope,
    "com.typesafe.akka" %% "akka-testkit"            % "2.6.20"             % scope
  )
}
