/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosavereminder.config

import org.mockito.IdiomaticMockito
import org.mockito.VerifyMacro.Once
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderRepository

import scala.concurrent.Future

class EmailDeleterSpec extends BaseSpec with IdiomaticMockito {
  "EmailDeleter" should {
    "delete reminders by nino at startup" in {
      val appConfig = mock[AppConfig]
      appConfig.excludedNinos returns List("AE111111D", "AE222222D")
      val repository = mock[HtsReminderRepository]
      repository.deleteHtsUser("AE111111D") returns Future.successful(Right(()))
      repository.deleteHtsUser("AE222222D") returns Future.successful(Left("Error occurred"))

      val _ = new EmailDeleter(appConfig, repository)

      repository.deleteHtsUser("AE111111D") wasCalled Once
      repository.deleteHtsUser("AE222222D") wasCalled Once
    }
  }
}
