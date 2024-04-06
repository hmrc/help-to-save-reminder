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
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import java.time.LocalDate
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.chaining.scalaUtilChainingOps

class EmailSenderActorSpec extends BaseSpec with BeforeAndAfterEach with IdiomaticMockito {

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
  private var sender: EmailSenderActor = _

  override def beforeEach(): Unit = {
    emailConnector = mock[EmailConnector]
    reminderRepository = mock[HtsReminderMongoRepository]
    reminderRepository.updateCallBackRef(*, *) returns Future.successful(true)
    val lockrepo = mock[MongoLockRepository]
    sender = new EmailSenderActor(servicesConfig, reminderRepository, emailConnector, lockrepo) {
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
      val uuids = (0 until 10).map(_ => EmailSenderActor.randomCallbackRef()).distinct.toList
      uuids.length shouldBe 10
      for (uuid <- uuids) {
        uuid should fullyMatch regex "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
      }
    }

    "should not do anything if couldn't update callback ref" in {
      reminderRepository.updateCallBackRef(*, *) returns Future.successful(false)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      result.pipe(getLeft).getMessage shouldEqual "Failed to update CallbackRef for the User: AE123456D"
      emailConnector.sendEmail(*, *)(*, *) wasNever called
      reminderRepository.updateNextSendDate(*, *) wasNever called
    }

    "send a request to the e-mail service" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
      val url = servicesConfig.baseUrl("email") + "/hmrc/email"
      val emailServiceRequest = SendTemplatedEmailRequest(
        to = List("email@test.com"),
        templateId = "hts_reminder_email",
        parameters = Map("name" -> "Luke Bishop", "month" -> "January"),
        force = true,
        eventUrl = servicesConfig.baseUrl("help-to-save-reminder") + "/help-to-save-reminder/bouncedEmail/my-ref"
      )
      emailConnector.sendEmail(emailServiceRequest, url)(*, *) was called
    }

    "update reminder to 25th of next month if sent on 1st" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
      reminderRepository.updateNextSendDate("AE123456D", nextSendDate = LocalDate.of(2020, 1, 25)) was called
    }

    "update reminder to 1st of next month if sent on 25th" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, currentDate = LocalDate.of(2020, 1, 25)))
      result shouldBe Right(())
      reminderRepository.updateNextSendDate("AE123456D", nextSendDate = LocalDate.of(2020, 2, 1)) was called
    }

    "not update 'next send date' when the schedule is empty" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val emptySchedule = userSchedule.copy(daysToReceive = Seq())
      val result = awaitEither(sender.sendScheduleMsg(emptySchedule, LocalDate.of(2020, 1, 25)))
      result shouldBe Right(())
      reminderRepository.updateNextSendDate(*, *) wasNever called
    }

    "not update 'next send date' if failed to send the e-mail" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(false)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 25)))
      result.pipe(getLeft).getMessage shouldEqual "Failed to send reminder for AE123456D my-ref"
      reminderRepository.updateNextSendDate(*, *) wasNever called
    }

    "return a Right when successfully sending the e-mail" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
    }

    "return a Right to parent even if didn't update the schedule" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(true)
      val emptySchedule = userSchedule.copy(daysToReceive = Seq())
      val result = awaitEither(sender.sendScheduleMsg(emptySchedule, LocalDate.of(2020, 1, 1)))
      result shouldBe Right(())
    }

    "return a Left if didn't update the schedule" in {
      emailConnector.sendEmail(*, *)(*, *) returns Future.successful(true)
      reminderRepository.updateNextSendDate(*, *) returns Future.successful(false)
      val result = awaitEither(sender.sendScheduleMsg(userSchedule, LocalDate.of(2020, 1, 1)))
      result.pipe(getLeft).getMessage shouldEqual "Failed to update nextSendDate for the User: AE123456D"
    }
  }
}

class HtsUserUpdateActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with IdiomaticMockito with BeforeAndAfterEach {

  private val userSchedule: HtsUserSchedule = HtsUserSchedule(Nino("AE123456D"), "email@test.com")

  private var repository: HtsReminderMongoRepository = _
  private var parent: TestProbe = _
  private var actor: ActorRef = _
  override def beforeEach(): Unit = {
    repository = mock[HtsReminderMongoRepository]
    parent = TestProbe()
    actor = parent.childActorOf(Props(new HtsUserUpdateActor(repository)))
  }

  "HtsUserSchedule message" must {
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
      within(1 second) { parent.expectNoMessage() }
    }
  }
}
