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
class Diagnostics @Inject() (repository: HtsReminderRepository)(implicit ec: ExecutionContext) extends Logging {
  for {
    duplicates <- repository.getDuplicateNinos
  } yield
    if (duplicates.nonEmpty) {
      for (nino -> count <- duplicates.sortBy(_._2).reverse) {
        logger.warn(s"[Diagnostics] Found duplicate account: $nino occurred $count")
      }
    } else {
      logger.info("[Diagnostics] No duplicates found")
    }
}
