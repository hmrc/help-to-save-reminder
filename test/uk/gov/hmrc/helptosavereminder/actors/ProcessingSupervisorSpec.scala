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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit._
import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually.eventually
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{HtsUserSchedule, HtsUserScheduleMsg, SendTemplatedEmailRequest, Stats}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, TimePeriodLockService}

import java.time.{LocalDate, ZoneId}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
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
    "should not do anything if couldn't update callback ref" in {
      val mongoApi = app.injector.instanceOf[MongoComponent]
      val emailConnector = mock[EmailConnector]
      val lockRepo = mock[MongoLockRepository]
      val reminderRepository = mock[HtsReminderMongoRepository]
      val actor =
        system.actorOf(Props(new ProcessingSupervisor(mongoApi, servicesConfig, emailConnector, lockRepo) {
          override lazy val repository: HtsReminderMongoRepository = reminderRepository
          override val lockKeeper: TimePeriodLockService = mock[TimePeriodLockService]
          when(lockKeeper.withRenewedLock(*)(*)).thenAnswer { invocation =>
            invocation.getArguments.head.asInstanceOf[Future[Unit]].map(_ => Some())
          }
        }))
      val scheduleMsg = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate = LocalDate.now)
      reminderRepository.findHtsUsersToProcess() returns Future.successful(Some(List(scheduleMsg.htsUserSchedule)))
      reminderRepository.updateCallBackRef(*, *) returns Future.successful(false)
      actor ! START
      within(1 second) {
        emailConnector.sendEmail(*, *)(*, *) wasNever called
        reminderRepository.updateNextSendDate(*, *) wasNever called
        val stats = Await.result(actor.ask(GET_STATS), 1 second).asInstanceOf[Stats]
        stats.emailsInFlight shouldBe List(scheduleMsg.htsUserSchedule.email)
        stats.dateFinished shouldNot equal(null)
        stats.dateAcknowledged shouldBe null
      }
    }

    "send a request to the e-mail service and update next send date" in {
      val mongoApi = app.injector.instanceOf[MongoComponent]
      val emailConnector = mock[EmailConnector]
      val lockRepo = mock[MongoLockRepository]
      val reminderRepository = mock[HtsReminderMongoRepository]
      val actor =
        system.actorOf(Props(new ProcessingSupervisor(mongoApi, servicesConfig, emailConnector, lockRepo) {
          override lazy val emailSenderActor: ActorRef = context.actorOf(
            Props(new EmailSenderActor(servicesConfig, repository, emailConnector)(ec, appConfig) {
              override val randomCallbackRef: () => String = () => "my-ref"
            }),
            "emailSender-actor"
          )
          override lazy val repository: HtsReminderMongoRepository = reminderRepository
          override val lockKeeper: TimePeriodLockService = mock[TimePeriodLockService]
          when(lockKeeper.withRenewedLock(*)(*)).thenAnswer { invocation =>
            invocation.getArguments.head.asInstanceOf[Future[Unit]].map(_ => Some())
          }
        }))
      reminderRepository.findHtsUsersToProcess() returns Future.successful(Some(List(userSchedule)))
      reminderRepository.updateCallBackRef(*, *) returns Future.successful(true)
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      actor ! START
      val url = servicesConfig.baseUrl("email") + "/hmrc/email"
      val monthName = LocalDate.now.getMonth.toString.toLowerCase.capitalize

      val emailServiceRequest = SendTemplatedEmailRequest(
        to = List("email@test.com"),
        templateId = "hts_reminder_email",
        parameters = Map("name" -> "Luke Bishop", "month" -> monthName),
        force = true,
        eventUrl = servicesConfig.baseUrl("help-to-save-reminder") + "/help-to-save-reminder/bouncedEmail/my-ref"
      )
      eventually {
        emailConnector.sendEmail(emailServiceRequest, url)(*, *) was called
        val nextSendDate = DateTimeFunctions.getNextSendDate(userSchedule.daysToReceive, LocalDate.now).get
        reminderRepository.updateNextSendDate("AE123456D", nextSendDate) was called

        val stats = Await.result(actor.ask(GET_STATS), 1 second).asInstanceOf[Stats]
        stats.emailsInFlight shouldBe List()
        stats.duplicates shouldBe List()
        stats.emailsComplete shouldBe List("email@test.com")
        stats.dateFinished shouldNot equal(null)
        stats.dateAcknowledged shouldNot equal(null)
      }
    }

    "send the correct request to the EmailSender actor" in {
      val mongoApi = app.injector.instanceOf[MongoComponent]
      val emailConnector = mock[EmailConnector]
      val lockRepo = mock[MongoLockRepository]
      val mockRepository = mock[HtsReminderMongoRepository]
      val emailSenderActorProbe = TestProbe()
      val processingSupervisor = TestActorRef(
        Props(new ProcessingSupervisor(mongoApi, servicesConfig, emailConnector, lockRepo) {
          override lazy val emailSenderActor: ActorRef = emailSenderActorProbe.ref
          override lazy val repository: HtsReminderMongoRepository = mockRepository
          override val lockKeeper: TimePeriodLockService = mock[TimePeriodLockService]
          when(lockKeeper.withRenewedLock(*)(*)).thenAnswer { invocation =>
            invocation.getArguments.head.asInstanceOf[Future[Unit]].map(_ => Some())
          }
        })
      )
      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))
      val mockObject = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate)
      mockRepository.findHtsUsersToProcess() returns Future.successful(Some(List(mockObject.htsUserSchedule)))
      processingSupervisor ! START

      eventually {
        emailSenderActorProbe.expectMsg(mockObject)
        emailSenderActorProbe.reply(SUCCESS)
      }
    }
  }
}
