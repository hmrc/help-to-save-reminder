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

import akka.actor.{ActorSystem, Props}
import akka.testkit._
import org.mockito.ArgumentMatchersSugar.*
import org.mockito.{ArgumentCaptor, IdiomaticMockito}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.ActorUtils.Acknowledge
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models._
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.lock.LockRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import scala.concurrent.Future
import scala.language.postfixOps

class EmailSenderActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with IdiomaticMockito {

  private val mockLockRepo = mock[LockRepository]
  private val httpClient = mock[HttpClient]
  override val servicesConfig: ServicesConfig = mock[ServicesConfig]
  private val emailConnector = mock[EmailConnector]
  private lazy val mockRepository = mock[HtsReminderMongoRepository]

  override def beforeAll: Unit =
    mockLockRepo.takeLock(*, *, *) returns Future.successful(true)

  "Email Sender Actor" must {
    "should send an Hts object to DB for saving" in {
      val emailSenderActor =
        system.actorOf(Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {}))

      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))
      val mockObject = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate)
      val requestCaptor = ArgumentCaptor.forClass(classOf[SendTemplatedEmailRequest])
      httpClient
        .POST[SendTemplatedEmailRequest, HttpResponse](*, requestCaptor.capture(), *)(*, *, *, *)
        .returns(Future.successful(HttpResponse(202, "")))

      mockRepository.updateNextSendDate(*, *) returns Future.successful(true)
      mockRepository.updateCallBackRef(*, *) returns Future.successful(true)

      emailSenderActor ! mockObject
      emailSenderActor ! UpdateCallBackSuccess(mockObject, "callBackSampleRef")
    }

    "send Acknowledge to parent when receiving Acknowledge" in {
      val parent = TestProbe()
      val emailSenderActor =
        parent.childActorOf(Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {}))
      parent.send(emailSenderActor, Acknowledge("test@email.com"))
      parent.expectMsg(Acknowledge("test@email.com"))
    }

    "send Acknowledge to parent when receiving Acknowledge" in {
      val emailSenderActor =
        system.actorOf(Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {}))
      emailSenderActor ! HtsUserScheduleMsg(HtsUserSchedule(Nino("AE123456D"), "email@test.com"), LocalDate.now())
    }
  }
}

class HtsUserUpdateActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with IdiomaticMockito {

  private val repository = mock[HtsReminderMongoRepository]
  private val userSchedule: HtsUserSchedule = HtsUserSchedule(Nino("AE123456D"), "email@test.com")

  "HtsUserSchedule message" must {
    "call updateNextSendDate for the NINO" in {
      repository.updateNextSendDate(*, *) returns Future.successful(true)
      val actor = system.actorOf(Props(new HtsUserUpdateActor(repository)))
      actor ! userSchedule.copy(nextSendDate = LocalDate.of(2020, 1, 2))
      repository.updateNextSendDate("AE123456D", LocalDate.of(2020, 1, 2)) was called
    }
    "Update next send date when updating user schedule" in {
      repository.updateNextSendDate(*, *) returns Future.successful(true)
      val actor = childActorOf(Props(new HtsUserUpdateActor(repository)))
      actor ! userSchedule
      expectMsg(Acknowledge("email@test.com"))
    }
    "Don't send Acknowledge if couldn't update" in {
      repository.updateNextSendDate(*, *) returns Future.successful(false)
      val actor = childActorOf(Props(new HtsUserUpdateActor(repository)))
      actor ! userSchedule
      expectNoMessage()
    }
  }

  "UpdateCallbackRef message" must {
    "update callback reference for the NINO" in {
      repository.updateCallBackRef(*, *) returns Future.successful(true)
      val actor = childActorOf(Props(new HtsUserUpdateActor(repository)))
      actor ! UpdateCallBackRef(HtsUserScheduleMsg(userSchedule, LocalDate.now()), "my-ref")
      repository.updateCallBackRef("AE123456D", "my-ref") was called
    }
    "Reply with success on success" in {
      repository.updateCallBackRef(*, *) returns Future.successful(true)
      val probe = TestProbe()
      val actor = probe.childActorOf(Props(new HtsUserUpdateActor(repository)))
      val reminder = HtsUserScheduleMsg(userSchedule, LocalDate.now())
      val callbackRef = "my-ref"
      probe.send(actor, UpdateCallBackRef(reminder, callbackRef))
      probe.expectMsg(UpdateCallBackSuccess(reminder, callbackRef))
    }
    "Do not reply on failure" in {
      repository.updateCallBackRef(*, *) returns Future.successful(false)
      val probe = TestProbe()
      val actor = probe.childActorOf(Props(new HtsUserUpdateActor(repository)))
      probe.send(actor, UpdateCallBackRef(HtsUserScheduleMsg(userSchedule, LocalDate.now()), "my-ref"))
      probe.expectNoMessage()
    }
  }
}
