import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "simple-reactivemongo"     % "8.0.0-play-28",
    "org.reactivemongo"       %% "reactivemongo-iteratees"  % "0.18.8", /* fix at 0.18.8 because that is what simple-reactivemongo 7.31.0-play-27 uses*/
    "org.reactivemongo"       %% "play2-reactivemongo"      % "0.18.8-play27", /* see above */
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"% "4.2.0",
    "uk.gov.hmrc"             %% "domain"                   % "5.11.0-play-27",
    "uk.gov.hmrc"             %% "mongo-lock"               % "7.0.0-play-28",
    "org.typelevel"           %% "cats-core"                % "2.6.0",
    "com.enragedginger"       %% "akka-quartz-scheduler"    % "1.9.0-akka-2.6.x"
  )


  val test = Seq(
    "uk.gov.hmrc"             %% "hmrctest"                 % "3.10.0-play-26"        % "test, it",
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "5.0.0-play-28"        % "test, it",
    "org.mockito"             %  "mockito-all"              % "1.10.19"               % "test, it",
    "com.typesafe.akka"       %% "akka-testkit"             % "2.6.10"                % "test, it",
    "com.typesafe.play"       %% "play-test"                % current                 % "test",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "5.0.0"                 % "test, it",
    "org.scalatest"           %% "scalatest"                % "3.0.9"                 % "test, it",
    "org.scalamock"           %% "scalamock"                % "5.1.0"                 % "test, it"
  )

}
