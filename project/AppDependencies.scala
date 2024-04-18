import sbt.*

object AppDependencies {

  private val playVersion           = "play-30"
  private val hmrcBootstrapVersion  = "8.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-$playVersion"        % "1.8.0",
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc"            %% s"domain-$playVersion"            % "9.0.0",
    "org.typelevel"          %% "cats-core"                       % "2.10.0",
    "io.github.samueleresca" %% "pekko-quartz-scheduler"          % "1.1.0-pekko-1.0.x"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVersion"  % hmrcBootstrapVersion % scope,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVersion" % "1.7.0"              % scope,
    "org.mockito"       %% "mockito-scala"                 % "1.17.31"            % scope
  )
}
