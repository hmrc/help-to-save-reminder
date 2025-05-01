import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "app.*",
    "test.*",
    "config.*",
    "metrics.*",
    "testOnlyDoNotUseInAppConf.*",
    "prod.*",
    "uk.gov.hmrc.helptosavereminder.controllers.test.*",
    "uk.gov.hmrc.helptosavereminder.actors.*",
    "uk.gov.hmrc.helptosavereminder.models.*",
    "uk.gov.hmrc.helptosavereminder.services.test.*",
    "uk.gov.hmrc.helptosavereminder.modules.*"
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
