/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavereminder.actors

import akka.actor._

import javax.inject.Singleton
import play.api.Logging
import uk.gov.hmrc.helptosavereminder.models.ActorUtils.Acknowledge
import uk.gov.hmrc.helptosavereminder.models.{HtsUserSchedule, UpdateCallBackRef, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository

import scala.concurrent.ExecutionContext

@Singleton
class HtsUserUpdateActor(repository: HtsReminderMongoRepository)(implicit ec: ExecutionContext)
    extends Actor with Logging {

  override def receive: Receive = {
    case htsUserSchedule: HtsUserSchedule => {
      repository.updateNextSendDate(htsUserSchedule.nino.value, htsUserSchedule.nextSendDate).map {
        case true => {
          logger.debug(s"Updated the User nextSendDate for ${htsUserSchedule.nino}")
          context.parent ! Acknowledge(htsUserSchedule.email)
        }
        case _ => {
          logger.warn(s"Failed to update nextSendDate for the User: ${htsUserSchedule.nino}")
        }
      }
    }

    case updateReminder: UpdateCallBackRef => {
      val origSender = sender
      repository
        .updateCallBackRef(updateReminder.reminder.htsUserSchedule.nino.value, updateReminder.callBackRefUrl)
        .map {
          case true => {
            logger.debug(
              s"Updated the User callBackRef for ${updateReminder.reminder.htsUserSchedule.nino.value} with value : ${updateReminder.callBackRefUrl}"
            )
            origSender ! UpdateCallBackSuccess(updateReminder.reminder, updateReminder.callBackRefUrl)
          }
          case _ =>
            logger.warn(
              s"Failed to update CallbackRef for the User: ${updateReminder.reminder.htsUserSchedule.nino.value}"
            )
        }
    }
  }
}
