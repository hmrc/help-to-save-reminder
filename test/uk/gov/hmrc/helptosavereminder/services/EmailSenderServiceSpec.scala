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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models._
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import java.time.LocalDate
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.chaining.scalaUtilChainingOps

class EmailSenderServiceSpec extends BaseSpec with BeforeAndAfterEach with MockitoSugar {
  private val userSchedule: HtsUserSchedule =
    HtsUserSchedule(
      Nino("AE123456D"),
      "email@test.com",
      firstName = "Luke",
      lastName = "Bishop",
      daysToReceive = Seq(1, 25)
    )
  private var emailConnector: EmailConnector = _
  private var reminderRepository: HtsReminderMongoRepository = _
  private var sender: EmailSenderService = _

  override def beforeEach(): Unit = {
    emailConnector = mock[EmailConnector]
    reminderRepository = mock[HtsReminderMongoRepository]
    when(reminderRepository.updateCallBackRef(any(), any())).thenReturn(Future.successful(true))
    val lockrepo = mock[MongoLockRepository]
    sender = new EmailSenderService(servicesConfig, reminderRepository, emailConnector, lockrepo) {
      override val randomCallbackRef: () => String = () => "my-ref"
    }
  }

  def awaitEither[T](future: Future[T]): Either[Throwable, T] =
    Await.result(future.map(Right(_)).recover(Left(_)), 1.second)

  def getLeft[L, R](either: Either[L, R]): L =
    either match {
      case Left(value) => value
      case Right(_)    => throw new RuntimeException("Was right, should've been left")
    }

  "Email Sender Actor" must {
    "generate different UUIDs every time" in {
      val uuids = (0 until 10).map(_ => EmailSenderService.randomCallbackRef()).distinct.toList
      uuids.length shouldBe 10
      for (uuid <- uuids)
        uuid should fullyMatch regex "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }

    "should not do anything if couldn't update callback ref" in {
      when(reminderRepository.updateCallBackRef(any(), any())).thenReturn(Future.successful(false))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      result.pipe(getLeft).getMessage shouldEqual "Failed to update CallbackRef for the User: AE123456D"
      verify(emailConnector, times(0)).sendEmail(any())(any(), any())
      verify(reminderRepository, times(0)).updateNextSendDate(any(), any())
    }

    "send a request to the e-mail service" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
      val emailServiceRequest = SendTemplatedEmailRequest(
        to = List("email@test.com"),
        templateId = "hts_reminder_email",
        parameters = Map("name" -> "Luke Bishop", "month" -> "January"),
        force = true,
        eventUrl = servicesConfig.baseUrl("help-to-save-reminder") + "/help-to-save-reminder/bouncedEmail/my-ref"
      )
      verify(emailConnector, times(1)).sendEmail(eqTo(emailServiceRequest))(any(), any())
    }

    "update reminder to 25th of next month if sent on 1st" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
      verify(reminderRepository, times(1)).updateNextSendDate("AE123456D", nextSendDate = LocalDate.of(2020, 1, 25))
    }

    "update reminder to 1st of next month if sent on 25th" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 25)))
      result shouldBe Right(())
      verify(reminderRepository, times(1)).updateNextSendDate("AE123456D", nextSendDate = LocalDate.of(2020, 2, 1))
    }

    "not update 'next send date' when the schedule is empty" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val emptySchedule = userSchedule.copy(daysToReceive = Seq())
      val result = awaitEither(sender.sendScheduleMsg(emptySchedule, LocalDate.of(2020, 1, 25)))
      result shouldBe Right(())
      verify(reminderRepository, times(0)).updateNextSendDate(any(), any())
    }

    "not update 'next send date' if failed to send the e-mail" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(false))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 25)))
      result.pipe(getLeft).getMessage shouldEqual "Failed to send reminder for AE123456D my-ref"
      verify(reminderRepository, times(0)).updateNextSendDate(any(), any())
    }

    "return a Right when successfully sending the e-mail" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
    }

    "return a Right to parent even if didn't update the schedule" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val emptySchedule = userSchedule.copy(daysToReceive = Seq())
      val result = awaitEither(sender.sendScheduleMsg(emptySchedule, LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
    }

    "return a Left if didn't update the schedule" in {
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(false))
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      result.pipe(getLeft).getMessage shouldEqual "Failed to update nextSendDate for the User: AE123456D"
    }
  }

  "sendWithStats" must {
    "not do anything if couldn't update callback ref" in {
      val emailConnector = mock[EmailConnector]
      val lockRepo = app.injector.instanceOf[MongoLockRepository]
      val reminderRepository = mock[HtsReminderMongoRepository]
      val emailSenderService = new EmailSenderService(servicesConfig, reminderRepository, emailConnector, lockRepo)
      val schedule = ReminderGenerator.nextReminder
      when(reminderRepository.findHtsUsersToProcess()).thenReturn(Future.successful(Some(List(schedule))))
      when(reminderRepository.updateCallBackRef(any(), any())).thenReturn(Future.successful(false))
      val stats = await(emailSenderService.sendWithStats()).get
      verify(emailConnector, times(0)).sendEmail(any())(any(), any())
      verify(reminderRepository, times(0)).updateNextSendDate(any(), any())
      stats.emailsInFlight shouldBe List(schedule.email)
      stats.dateFinished shouldNot equal(null)
      stats.dateAcknowledged shouldBe null
    }

    "send a request to the e-mail service and update next send date" in {
      val emailConnector = mock[EmailConnector]
      val lockRepo = app.injector.instanceOf[MongoLockRepository]
      val reminderRepository = mock[HtsReminderMongoRepository]
      val emailSenderService = new EmailSenderService(servicesConfig, reminderRepository, emailConnector, lockRepo) {
        override val randomCallbackRef: () => String = () => "my-ref"
      }
      when(reminderRepository.findHtsUsersToProcess()).thenReturn(Future.successful(Some(List(userSchedule))))
      when(reminderRepository.updateCallBackRef(any(), any())).thenReturn(Future.successful(true))
      when(emailConnector.sendEmail(any())(any(), any())).thenReturn(Future.successful(true))
      when(reminderRepository.updateNextSendDate(any(), any())).thenReturn(Future.successful(true))
      val stats = await(emailSenderService.sendWithStats()).get
      val monthName = LocalDate.now.getMonth.toString.toLowerCase.capitalize

      val emailServiceRequest = SendTemplatedEmailRequest(
        to = List("email@test.com"),
        templateId = "hts_reminder_email",
        parameters = Map("name" -> "Luke Bishop", "month" -> monthName),
        force = true,
        eventUrl = servicesConfig.baseUrl("help-to-save-reminder") + "/help-to-save-reminder/bouncedEmail/my-ref"
      )
      verify(emailConnector, times(1)).sendEmail(eqTo(emailServiceRequest))(any(), any())
      val nextSendDate = DateTimeFunctions.getNextSendDate(userSchedule.daysToReceive, LocalDate.now).get
      verify(reminderRepository, times(1)).updateNextSendDate("AE123456D", nextSendDate)

      stats.emailsInFlight shouldBe List()
      stats.duplicates shouldBe List()
      stats.emailsComplete shouldBe List("email@test.com")
      stats.dateFinished shouldNot equal(null)
      stats.dateAcknowledged shouldNot equal(null)
    }

    "send e-mail through EmailSender" in {
      val emailConnector = mock[EmailConnector]
      val lockRepo = app.injector.instanceOf[MongoLockRepository]
      val mockRepository = mock[HtsReminderMongoRepository]
      val emailSenderService = new EmailSenderService(servicesConfig, mockRepository, emailConnector, lockRepo) {
        override def sendScheduleMsg(reminder: HtsUserSchedule, currentDate: LocalDate): Future[Unit] =
          Future.successful(())
      }
      val schedule = ReminderGenerator.nextReminder
      when(mockRepository.findHtsUsersToProcess()).thenReturn(Future.successful(Some(List(schedule))))
      await(emailSenderService.sendWithStats()).get
    }
  }
}
