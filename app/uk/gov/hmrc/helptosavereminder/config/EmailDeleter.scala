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

import play.api.Logging
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EmailDeleter @Inject() (appConfig: AppConfig, repository: HtsReminderRepository)(
  implicit ec: ExecutionContext
) extends Logging {
  for (nino <- appConfig.excludedNinos) {
    for {
      outcome <- repository.deleteHtsUser(nino)
    } outcome match {
      case Left(error) => logger.error(error, new Exception(error))
      case Right(())   => logger.info(s"Successfully cleared reminders for a user with nino: $nino")
    }
  }
}
