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

import java.util.UUID
import java.time.{LocalDate, ZoneId}
import java.util.UUID

import akka.actor._
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.{HtsReminderTemplate, HtsUser, SendTemplatedEmailRequest, UpdateCallBackRef, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailSenderActor @Inject()(
  http: HttpClient,
  environment: Environment,
  val runModeConfiguration: Configuration,
  servicesConfig: ServicesConfig,
  repository: HtsReminderMongoRepository,
  emailConnector: EmailConnector)(implicit ec: ExecutionContext, implicit val appConfig: AppConfig)
    extends Actor {

  implicit lazy val hc = HeaderCarrier()
  lazy val htsUserUpdateActor: ActorRef =
    context.actorOf(
      Props(classOf[HtsUserUpdateActor], http, environment, runModeConfiguration, servicesConfig, repository, ec),
      "htsUserUpdate-actor")

  val sendEmailTemplateId = appConfig.sendEmailTemplateId
  val nameParam = appConfig.nameParam
  val monthParam = appConfig.monthParam
  val callBackUrlParam = appConfig.callBackUrlParam

  override def receive: Receive = {

    case htsUserReminder: HtsUser => {

      val callBackRef = UUID.randomUUID().toString
      htsUserUpdateActor ! UpdateCallBackRef(htsUserReminder, callBackRef)

    }

    case successReminder: UpdateCallBackSuccess => {

      val reminder = successReminder.reminder

      val template =
        HtsReminderTemplate(
          reminder.email,
          reminder.firstName + " " + reminder.lastName,
          successReminder.callBackRefUrl)

      sendReceivedTemplatedEmail(template).map({
        case true => {
          val nextSendDate =
            DateTimeFunctions.getNextSendDate(reminder.daysToReceive, LocalDate.now(ZoneId.of("Europe/London")))
          nextSendDate match {
            case Some(x) =>
              val updatedReminder = reminder.copy(nextSendDate = x)
              htsUserUpdateActor ! updatedReminder
            case None =>
          }
        }
        case false => Logger.error(s"nextSendDate for User: $template cannot be updated.")
      })

    }

  }

  def sendReceivedTemplatedEmail(template: HtsReminderTemplate)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val callBackUrl = s"${servicesConfig.baseUrl("help-to-save-reminder")}/help-to-save-reminder/bouncedEmail/" + template.callBackUrlRef

    Logger.debug(s"The callback URL = $callBackUrl")

    val monthName = LocalDate.now(ZoneId.of("Europe/London")).getMonth.toString.toLowerCase.capitalize

    val request = SendTemplatedEmailRequest(
      List(template.email),
      sendEmailTemplateId,
      Map(nameParam -> template.name, monthParam -> monthName),
      callBackUrl)

    sendEmail(request)

  }

  private def sendEmail(request: SendTemplatedEmailRequest)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val url = s"${servicesConfig.baseUrl("email")}/hmrc/email"

    http.POST(url, request, Seq(("Content-Type", "application/json"))) map { response =>
      response.status match {

        case 202 => Logger.debug(s"[EmailSenderActor] Email sent: ${response.body}"); true
        case _   => Logger.error(s"[EmailSenderActor] Email not sent: ${response.body}"); false

      }
    }
  }
}
