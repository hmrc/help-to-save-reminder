/*
 * Copyright 2021 HM Revenue & Customs
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
import akka.actor._
import com.google.inject.Inject

import javax.inject.Singleton
import play.api.Logging
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator.nextReminder
import uk.gov.hmrc.helptosavereminder.models.{HtsReminderTemplate, HtsUserScheduleMsg, SendTemplatedEmailRequest, UpdateCallBackRef, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailSenderActor @Inject() (
  servicesConfig: ServicesConfig,
  repository: HtsReminderMongoRepository,
  emailConnector: EmailConnector
)(implicit ec: ExecutionContext, implicit val appConfig: AppConfig)
    extends Actor with Logging {

  implicit lazy val hc = HeaderCarrier()
  lazy val htsUserUpdateActor: ActorRef =
    context.actorOf(Props(classOf[HtsUserUpdateActor], repository, ec), "htsUserUpdate-actor")

  val sendEmailTemplateId = appConfig.sendEmailTemplateId
  val nameParam = appConfig.nameParam
  val monthParam = appConfig.monthParam
  val callBackUrlParam = appConfig.callBackUrlParam

  override def receive: Receive = {

    case htsUserReminderMsg: HtsUserScheduleMsg => {

      val callBackRef = UUID.randomUUID().toString
      logger.info(s"New callBackRef [$callBackRef]")
      htsUserUpdateActor ! UpdateCallBackRef(htsUserReminderMsg, callBackRef)
    }

    case successReminder: UpdateCallBackSuccess => {

      val shouldSendReminder = successReminder.reminder.htsUserSchedule.accountClosingDate match {
        case Some(closingDate) =>
          logger.info(s"accountClosingDate: [$closingDate]")
          closingDate.isAfter(nextReminder.nextSendDate)
        case _                 => true
      }

      if (shouldSendReminder) {
        logger.info("It should shouldSendReminder")
        val reminder = successReminder.reminder.htsUserSchedule
        val monthName = successReminder.reminder.currentDate.getMonth.toString.toLowerCase.capitalize

        val ref = successReminder.callBackRefUrl
        val template = {

          def format(name: String) = name.toLowerCase.capitalize

          HtsReminderTemplate(
            reminder.email,
            format(reminder.firstName) + " " + format(reminder.lastName),
            ref,
            monthName
          )
        }

        logger.info(s"Sending reminder for $ref")
        sendReceivedTemplatedEmail(template).map({
          case true => {
            logger.info(s"Sent reminder for $ref")
            val nextSendDate =
              DateTimeFunctions.getNextSendDate(reminder.daysToReceive, successReminder.reminder.currentDate)
            nextSendDate match {
              case Some(x) =>
                val updatedReminder = reminder.copy(nextSendDate = x)
                htsUserUpdateActor ! updatedReminder
              case None =>
            }
          }
          case false =>
            logger.warn(s"Failed to send reminder for ${reminder.nino.value} $ref")
        })

      } else {
        val nino = successReminder.reminder.htsUserSchedule.nino.toString
        logger.info(s"Not sending reminder for [$nino]")
        repository.deleteHtsUser(nino) map {
          case Right(()) => {
            logger.info(s"Successfully deleted nino: [$nino]")
          }
          case Left(_) => logger.error(s"Failed to delete nino: [$nino]")
        }
      }
    }

  }

  def sendReceivedTemplatedEmail(template: HtsReminderTemplate)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val callBackUrl = s"${servicesConfig.baseUrl("help-to-save-reminder")}/help-to-save-reminder/bouncedEmail/" + template.callBackUrlRef

    logger.debug(s"The callback URL = $callBackUrl")

    val request = SendTemplatedEmailRequest(
      List(template.email),
      sendEmailTemplateId,
      Map(nameParam -> template.name, monthParam -> template.monthName),
      callBackUrl
    )

    sendEmail(request)

  }

  private def sendEmail(request: SendTemplatedEmailRequest)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val url = s"${servicesConfig.baseUrl("email")}/hmrc/email"

    emailConnector.sendEmail(request, url) map { response =>
      response match {
        case true => logger.debug(s"[EmailSenderActor] Email sent: $request"); true
        case _    => logger.debug(s"[EmailSenderActor] Email not sent: $request"); false
      }
    }
  }
}
