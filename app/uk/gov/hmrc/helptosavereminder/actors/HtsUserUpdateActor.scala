/*
 * Copyright 2020 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavereminder.models.{Reminder, UpdateCallBackRef, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext

@Singleton
class HtsUserUpdateActor(
  http: HttpClient,
  environment: Environment,
  val runModeConfiguration: Configuration,
  servicesConfig: ServicesConfig,
  repository: HtsReminderMongoRepository)(implicit ec: ExecutionContext)
    extends Actor {

  lazy val htsUserUpdateActor: ActorRef =
    context.actorOf(Props(classOf[HtsUserUpdateActor], repository, ec), "htsUserUpdate-actor")

  lazy val origSender = context.actorOf(
    Props(classOf[EmailSenderActor], http, environment, runModeConfiguration, servicesConfig, repository, ec),
    "emailSender-actor")

  override def receive: Receive = {
    case reminder: Reminder => {

      repository.updateNextSendDate(reminder.nino.nino).map {

        case true => {
          Logger.info("Updated the User nextSendDate for " + reminder.nino)
        }

        case _ => //Failure
      }

      //TODO: Update reminder in mongo to have a new next send date
    }

    case updateReminder: UpdateCallBackRef => {

      repository.updateCallBackRef(updateReminder.reminder.nino.nino, updateReminder.callBackRefUrl).map {

        case true => {
          Logger.info("Updated the User callBackRef for " + updateReminder.reminder.nino.nino)
          Logger.info("Type of sender actor is " + origSender.getClass)
          origSender ! UpdateCallBackSuccess(updateReminder.reminder)
        }

        case _ => //Failure
      }

      //TODO: Update reminder in mongo to have a new next send date
    }

  }
}
