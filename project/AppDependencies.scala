import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "org.reactivemongo"       %% "play2-reactivemongo"      % "0.17.1-play26",
    "uk.gov.hmrc"             %% "simple-reactivemongo"     % "7.30.0-play-26",
    "org.reactivemongo"       %% "reactivemongo-iteratees"  % "0.20.3",
    "uk.gov.hmrc"             %% "bootstrap-backend-play-26"% "2.24.0",
    "uk.gov.hmrc"             %% "domain"                   % "5.9.0-play-26",
    "uk.gov.hmrc"             %% "mongo-lock"               % "6.23.0-play-26",
    "org.typelevel"           %% "cats-core"                % "2.1.1",
    "uk.gov.hmrc"             %% "auth-client"              % "2.32.0-play-26",
  "com.enragedginger"         %% "akka-quartz-scheduler"      % "1.8.1-akka-2.5.x"
  )


  val test = Seq(
    "org.reactivemongo"       %% "play2-reactivemongo"      % "0.17.1-play26",
    "uk.gov.hmrc"             %% "hmrctest"                 % "3.9.0-play-26",
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.21.0-play-26",
    "org.mockito"             %  "mockito-all"              % "1.9.5",
    "uk.gov.hmrc"             %% "domain"                   % "5.9.0-play-26",
    "org.reactivemongo"       %% "reactivemongo-iteratees"  % "0.20.3",
    "com.typesafe.akka"       %% "akka-testkit"             % "2.5.23",
    "uk.gov.hmrc"             %% "domain"                   % "5.9.0-play-26",
    "com.typesafe.play"       %% "play-test"                % current                 % "test",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "3.1.2"                 % "test, it",
    "org.scalatest"           %% "scalatest"                % "3.0.8"                 % "test, it",
    "org.scalamock"           %% "scalamock"                % "4.4.0"                 % "test, it"
  )

}
