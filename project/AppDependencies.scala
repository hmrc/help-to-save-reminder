import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  private val playVersion = "play-28"

  val compile = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"      % s"0.70.0",
    "uk.gov.hmrc"             %% s"bootstrap-backend-$playVersion" % "5.12.0",
    "uk.gov.hmrc"             %% "domain"                    % s"6.2.0-$playVersion",
    "org.typelevel"           %% "cats-core"                 % "2.6.0",
    "com.enragedginger"       %% "akka-quartz-scheduler"     % "1.9.0-akka-2.6.x",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.5" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.5" % Provided cross CrossVersion.full
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"  % "0.70.0"               % "test, it",
    "org.mockito"             %  "mockito-all"              % "1.10.19"               % "test, it",
    "com.typesafe.akka"       %% "akka-testkit"             % "2.6.14"                % "test, it",
    "com.typesafe.play"       %% "play-test"                % current                 % "test",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "5.1.0"                 % "test, it",
    "org.scalatestplus"       %% "scalatestplus-mockito"    % "1.0.0-M2"              % "test, it",
    "org.scalatest"           %% "scalatest"                % "3.2.9"                 % "test, it",
    "org.scalamock"           %% "scalamock"                % "5.1.0"                 % "test, it",
    "com.vladsch.flexmark"    % "flexmark-all"              % "0.35.10"               % "test, it"
  )

}
