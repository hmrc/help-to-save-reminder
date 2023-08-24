import play.core.PlayVersion.current
import sbt.*

object AppDependencies {

  private val playVersion = "play-28"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"      % s"0.70.0",
    "uk.gov.hmrc"             %% s"bootstrap-backend-$playVersion" % "5.25.0",
    "uk.gov.hmrc"             %% "domain"                    % s"6.2.0-$playVersion",
    "org.typelevel"           %% "cats-core"                 % "2.6.0",
    "com.enragedginger"       %% "akka-quartz-scheduler"     % "1.9.0-akka-2.6.x"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"  % "0.70.0"                % scope,
    "com.typesafe.akka"       %% "akka-testkit"             % "2.6.19"                % scope,
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "5.1.0"                 % scope,
    "org.scalatestplus"       %% "scalatestplus-mockito"    % "1.0.0-M2"              % scope,
    "org.scalatest"           %% "scalatest"                % "3.2.9"                 % scope,
    "org.scalamock"           %% "scalamock"                % "5.1.0"                 % scope,
  )
}
