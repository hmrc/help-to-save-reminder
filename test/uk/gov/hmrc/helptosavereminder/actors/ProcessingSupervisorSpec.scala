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

import akka.actor.ActorSystem
import akka.testkit._
import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import play.api.test.Helpers.await
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{HtsUserSchedule, SendTemplatedEmailRequest}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.services.EmailSenderService
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import java.time.LocalDate
import scala.concurrent.Future
import scala.language.postfixOps

class ProcessingSupervisorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with IdiomaticMockito {

  private val userSchedule: HtsUserSchedule =
    HtsUserSchedule(
      Nino("AE123456D"),
      "email@test.com",
      firstName = "Luke",
      lastName = "Bishop",
      daysToReceive = Seq(1, 25)
    )

  "processing supervisor" must {
    "not do anything if couldn't update callback ref" in {
      val emailConnector = mock[EmailConnector]
      val lockRepo = app.injector.instanceOf[MongoLockRepository]
      val reminderRepository = mock[HtsReminderMongoRepository]
      val emailSenderService = new EmailSenderService(servicesConfig, reminderRepository, emailConnector, lockRepo)
      val schedule = ReminderGenerator.nextReminder
      reminderRepository.findHtsUsersToProcess() returns Future.successful(Some(List(schedule)))
      reminderRepository.updateCallBackRef(*, *) returns Future.successful(false)
      val stats = await(emailSenderService.sendWithStats()).get
      emailConnector.sendEmail(*, *)(*, *) wasNever called
      reminderRepository.updateNextSendDate(*, *) wasNever called
      stats.emailsInFlight shouldBe List(schedule.email)
      stats.dateFinished shouldNot equal(null)
      stats.dateAcknowledged shouldBe null
    }

    "send a request to the e-mail service and update next send date" in {
      val mongoApi = app.injector.instanceOf[MongoComponent]
      val emailConnector = mock[EmailConnector]
      val lockRepo = app.injector.instanceOf[MongoLockRepository]
      val reminderRepository = mock[HtsReminderMongoRepository]
      val emailSenderService = new EmailSenderService(servicesConfig, reminderRepository, emailConnector, lockRepo) {
        override val randomCallbackRef: () => String = () => "my-ref"
      }
      reminderRepository.findHtsUsersToProcess() returns Future.successful(Some(List(userSchedule)))
      reminderRepository.updateCallBackRef(*, *) returns Future.successful(true)
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val stats = await(emailSenderService.sendWithStats()).get
      val url = servicesConfig.baseUrl("email") + "/hmrc/email"
      val monthName = LocalDate.now.getMonth.toString.toLowerCase.capitalize

      val emailServiceRequest = SendTemplatedEmailRequest(
        to = List("email@test.com"),
        templateId = "hts_reminder_email",
        parameters = Map("name" -> "Luke Bishop", "month" -> monthName),
        force = true,
        eventUrl = servicesConfig.baseUrl("help-to-save-reminder") + "/help-to-save-reminder/bouncedEmail/my-ref"
      )
      emailConnector.sendEmail(emailServiceRequest, url)(*, *) was called
      val nextSendDate = DateTimeFunctions.getNextSendDate(userSchedule.daysToReceive, LocalDate.now).get
      reminderRepository.updateNextSendDate("AE123456D", nextSendDate) was called

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
      mockRepository.findHtsUsersToProcess() returns Future.successful(Some(List(schedule)))
      await(emailSenderService.sendWithStats()).get
    }
  }
}
