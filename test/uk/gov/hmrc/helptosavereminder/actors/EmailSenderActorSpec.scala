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
import akka.testkit._
import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually.eventually
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.ActorUtils.Acknowledge
import uk.gov.hmrc.helptosavereminder.models._
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository

import java.time.LocalDate
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class EmailSenderActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with BeforeAndAfterEach with IdiomaticMockito {

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
  private var actor: ActorRef = _
  private var parent: TestProbe = _

  override def beforeEach(): Unit = {
    emailConnector = mock[EmailConnector]
    reminderRepository = mock[HtsReminderMongoRepository]
    reminderRepository.updateCallBackRef(*, *) returns Future.successful(true)
    parent = TestProbe()
    actor = parent.childActorOf(Props(new EmailSenderActor(servicesConfig, reminderRepository, emailConnector) {
      override val randomCallbackRef: () => String = () => "my-ref"
    }))
  }

  "Email Sender Actor" must {
    "generate different UUIDs every time" in {
      val uuids = (0 until 10).map(_ => EmailSenderActor.randomCallbackRef()).distinct.toList
      uuids.length shouldBe 10
      for (uuid <- uuids) {
        uuid should fullyMatch regex "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
      }
    }

    "should not do anything if couldn't update callback ref" in {
      reminderRepository.updateCallBackRef(*, *) returns Future.successful(false)
      parent.send(actor, HtsUserScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      within(1 second) {
        emailConnector.sendEmail(*, *)(*, *) wasNever called
        reminderRepository.updateNextSendDate(*, *) wasNever called
        parent.expectNoMessage()
      }
    }

    "send a request to the e-mail service" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      actor ! HtsUserScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 1))
      val url = servicesConfig.baseUrl("email") + "/hmrc/email"
      val emailServiceRequest = SendTemplatedEmailRequest(
        to = List("email@test.com"),
        templateId = "hts_reminder_email",
        parameters = Map("name" -> "Luke Bishop", "month" -> "January"),
        force = true,
        eventUrl = servicesConfig.baseUrl("help-to-save-reminder") + "/help-to-save-reminder/bouncedEmail/my-ref"
      )
      eventually { emailConnector.sendEmail(emailServiceRequest, url)(*, *) was called }
    }

    "update reminder to 25th of next month if sent on 1st" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      actor ! HtsUserScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 1))
      eventually {
        reminderRepository.updateNextSendDate("AE123456D", nextSendDate = LocalDate.of(2020, 1, 25)) was called
      }
    }

    "update reminder to 1st of next month if sent on 25th" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      actor ! HtsUserScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 25))
      eventually {
        reminderRepository.updateNextSendDate("AE123456D", nextSendDate = LocalDate.of(2020, 2, 1)) was called
      }
    }

    "not update 'next send date' when the schedule is empty" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val emptySchedule = userSchedule.copy(daysToReceive = Seq())
      actor ! HtsUserScheduleMsg(emptySchedule, LocalDate.of(2020, 1, 25))
      within(1 second) { reminderRepository.updateNextSendDate(*, *) wasNever called }
    }

    "not update 'next send date' if failed to send the e-mail" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(false)
      actor ! HtsUserScheduleMsg(userSchedule, LocalDate.of(2020, 1, 25))
      within(1 second) { reminderRepository.updateNextSendDate(*, *) wasNever called }
    }

    "send Acknowledge to parent when successfully sending the e-mail" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      parent.send(actor, HtsUserScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      parent.expectMsg(Acknowledge("email@test.com"))
    }

    "send Acknowledge to parent even if didn't update the schedule" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val emptySchedule = userSchedule.copy(daysToReceive = Seq())
      val message = HtsUserScheduleMsg(emptySchedule, LocalDate.of(2020, 1, 1))
      parent.send(actor, message)
      parent.expectMsg(Acknowledge("email@test.com"))
    }

    "not send Acknowledge if didn't update teh schedule" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(false)
      val message = HtsUserScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1))
      parent.send(actor, message)
      parent.expectNoMessage()
    }

    "send Acknowledge to parent when receiving Acknowledge" in {
      parent.send(actor, Acknowledge("test@email.com"))
      parent.expectMsg(Acknowledge("test@email.com"))
    }
  }
}

class HtsUserUpdateActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with IdiomaticMockito with BeforeAndAfterEach {

  private val userSchedule: HtsUserSchedule = HtsUserSchedule(Nino("AE123456D"), "email@test.com")

  private var count = 0
  private var repository: HtsReminderMongoRepository = _
  private var parent: TestProbe = _
  private var actor: ActorRef = _
  override def beforeEach(): Unit = {
    repository = mock[HtsReminderMongoRepository]
    parent = TestProbe()
    actor = parent.childActorOf(Props(new HtsUserUpdateActor(repository)))
  }

  "HtsUserSchedule message" must {
    count = count + 1
    "call updateNextSendDate for the NINO" in {
      repository.updateNextSendDate(*, *) returns Future.successful(true)
      actor ! userSchedule.copy(nextSendDate = LocalDate.of(2020, 1, 2))
      eventually { repository.updateNextSendDate("AE123456D", LocalDate.of(2020, 1, 2)) was called }
    }
    "Acknowledge if successfully updated" in {
      repository.updateNextSendDate(*, *) returns Future.successful(true)
      parent.send(actor, userSchedule)
      parent.expectMsg(Acknowledge("email@test.com"))
    }
    "Don't send Acknowledge if couldn't update" in {
      repository.updateNextSendDate(*, *) returns Future.successful(false)
      parent.send(actor, userSchedule)
      parent.expectNoMessage()
    }
    "count must be 4" in {
      count should equal(4)
    }
  }

  "UpdateCallbackRef message" must {
    "update callback reference for the NINO" in {
      repository.updateCallBackRef(*, *) returns Future.successful(true)
      actor ! UpdateCallBackRef(HtsUserScheduleMsg(userSchedule, LocalDate.now()), "my-ref")
      eventually { repository.updateCallBackRef("AE123456D", "my-ref") was called }
    }
    "Reply with success on success" in {
      repository.updateCallBackRef(*, *) returns Future.successful(true)
      val reminder = HtsUserScheduleMsg(userSchedule, LocalDate.now())
      parent.send(actor, UpdateCallBackRef(reminder, "my-ref"))
      parent.expectMsg(UpdateCallBackSuccess(reminder, "my-ref"))
    }
    "Do not reply on failure" in {
      repository.updateCallBackRef(*, *) returns Future.successful(false)
      parent.send(actor, UpdateCallBackRef(HtsUserScheduleMsg(userSchedule, LocalDate.now()), "my-ref"))
      parent.expectNoMessage()
    }
  }
}
