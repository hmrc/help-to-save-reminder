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

package uk.gov.hmrc.helptosavereminder.services

import play.api.Logging
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models._
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

@Singleton
class EmailSenderService @Inject() (
  servicesConfig: ServicesConfig,
  repository: HtsReminderMongoRepository,
  emailConnector: EmailConnector,
  lockrepo: MongoLockRepository
)(implicit ec: ExecutionContext, implicit val appConfig: AppConfig)
    extends Logging {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  private val sendEmailTemplateId = appConfig.sendEmailTemplateId
  private val nameParam = appConfig.nameParam
  private val monthParam = appConfig.monthParam

  private val repoLockPeriod: Int = appConfig.repoLockPeriod
  private lazy val lockService = LockService(lockrepo, lockId = "emailProcessing", ttl = repoLockPeriod.seconds)

  val randomCallbackRef: () => String = () => EmailSenderService.randomCallbackRef()

  private def format(name: String) = name.toLowerCase.capitalize
  private def check(msg: => String)(future: Future[Boolean]) =
    for (successful <- future) yield if (!successful) throw new RuntimeException(msg)

  def sendScheduleMsg(reminder: HtsUserSchedule, currentDate: LocalDate): Future[Unit] = {
    val callBackRef = randomCallbackRef()
    logger.info(s"New callBackRef $callBackRef")
    for {
      _ <- repository.updateCallBackRef(reminder.nino.value, callBackRef) pipe
            check(s"Failed to update CallbackRef for the User: ${reminder.nino.value}")
      template = HtsReminderTemplate(
        email = reminder.email,
        name = format(reminder.firstName) + " " + format(reminder.lastName),
        callBackUrlRef = callBackRef,
        monthName = currentDate.getMonth.toString.toLowerCase.capitalize
      )
      _ = logger.info(s"Sending reminder for $callBackRef")
      _ <- sendReceivedTemplatedEmail(template) pipe check(
            s"Failed to send reminder for ${reminder.nino.value} $callBackRef"
          )
      _ = logger.info(s"Sent reminder for $callBackRef")
      _ <- DateTimeFunctions.getNextSendDate(reminder.daysToReceive, currentDate) match {
            case Some(nextSendDate) =>
              repository.updateNextSendDate(reminder.nino.value, nextSendDate) pipe
                check(s"Failed to update nextSendDate for the User: ${reminder.nino}")
            case None => Future.successful()
          }
    } yield ()
  }

  private def sendReceivedTemplatedEmail(template: HtsReminderTemplate)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val callBackUrl = s"${servicesConfig.baseUrl("help-to-save-reminder")}/help-to-save-reminder/bouncedEmail/" + template.callBackUrlRef
    val request = SendTemplatedEmailRequest(
      List(template.email),
      sendEmailTemplateId,
      Map(nameParam -> template.name, monthParam -> template.monthName),
      force = true,
      callBackUrl
    )
    emailConnector.sendEmail(request, s"${servicesConfig.baseUrl("email")}/hmrc/email")
  }

  def sendBatch(): Future[Option[List[Either[String, String]]]] = {
    val scheduleTake: Int = appConfig.scheduleTake
    val currentDate = LocalDate.now(ZoneId.of("Europe/London"))
    lockService withLock {
      repository.findHtsUsersToProcess().flatMap {
        case None | Some(Nil) =>
          logger.info(s"[EmailSenderService] no requests pending")
          Future.successful(List())
        case Some(requests) =>
          val take = requests.take(scheduleTake)
          logger.info(s"[EmailSenderService] ${requests.size} found but only taking ${take.size} requests)")
          take
            .map { request =>
              sendScheduleMsg(request, currentDate)
                .map(_ => Right(request.email))
                .recover { exception =>
                  logger.error(s"[EmailSenderService] Failed to send an e-mail to ${request.email}", exception)
                  Left(request.email)
                }
            }
            .pipe(Future.sequence(_))
      }
    } tap (_ map {
      case Some(_) => logger.info(s"[EmailSenderService] OBTAINED mongo lock")
      case _       => logger.info(s"[EmailSenderService] failed to OBTAIN mongo lock.")
    })
  }

  def sendWithStats(): Future[Option[Stats]] = {
    val started = LocalDateTime.now
    for {
      results <- sendBatch()
    } yield {
      val finished = LocalDateTime.now
      results.map { emails =>
        Stats(
          emailsInFlight = emails.flatMap(_.swap.toOption),
          emailsComplete = emails.flatMap(_.toOption),
          duplicates = List(),
          dateStarted = started.toString,
          dateFinished = finished.toString,
          dateAcknowledged = if (emails.forall(_.isRight)) finished.toString else null
        )
      }
    }
  }
}

object EmailSenderService {
  def randomCallbackRef(): String = UUID.randomUUID().toString
}
