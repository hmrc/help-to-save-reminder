import sbt.*

object AppDependencies {

  private val playVersion = "play-30"
  private val hmrcBootstrapVersion = "9.13.0"
  private val hmrcMongoVersion = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-$playVersion"        % hmrcMongoVersion,
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc"            %% s"domain-$playVersion"            % "12.1.0",
    "org.typelevel"          %% "cats-core"                       % "2.13.0",
    "io.github.samueleresca" %% "pekko-quartz-scheduler"          % "1.2.2-pekko-1.0.x"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVersion"  % hmrcBootstrapVersion % scope,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion     % scope
  )
}
