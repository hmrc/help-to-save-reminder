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
    "views.html.*",
    "prod.*",
    "uk.gov.hmrc.helptosavereminder.controllers.test.*",
    "uk.gov.hmrc.helptosavereminder.actors.*",
    "uk.gov.hmrc.helptosavereminder.models.test.*",
    "uk.gov.hmrc.helptosavereminder.services.test.*"
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
