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

import akka.actor.{ActorSystem, Props}
import akka.testkit._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{HtsUserScheduleMsg, SendTemplatedEmailRequest, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class EmailSenderActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with MockitoSugar {

  val mockLockRepo = mock[LockRepository]

  val httpClient = mock[HttpClient]

  val env = mock[play.api.Environment]

  override val servicesConfig = mock[ServicesConfig]

  val emailConnector = mock[EmailConnector]

  val mongoApi = app.injector.instanceOf[play.modules.reactivemongo.ReactiveMongoComponent]

  lazy val mockRepository = mock[HtsReminderMongoRepository]

  override def beforeAll =
    when(mockLockRepo.lock(anyString, anyString, any())) thenReturn Future.successful(true)

  "Email Sender Actor" must {

    "should send an Hts object to DB for saving" in {

      val emailSenderActor = TestActorRef(
        Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {}),
        "email-sender-actor-1"
      )

      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))

      val mockObject = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate)

      val requestCaptor = ArgumentCaptor.forClass(classOf[SendTemplatedEmailRequest])

      when(
        httpClient.POST[SendTemplatedEmailRequest, HttpResponse](
          anyString,
          requestCaptor.capture(),
          any[Seq[(String, String)]]
        )(any(), any(), any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(HttpResponse(202, "")))

      //TO test the update is correct you could use a UUID generator passed into
      when(mockRepository.updateNextSendDate(any(), any()))
        .thenReturn(Future.successful(true))

      when(mockRepository.updateCallBackRef(any(), any()))
        .thenReturn(Future.successful(true))

      within(5 seconds) {

        emailSenderActor ! mockObject

//        emailSenderActor ! UpdateCallBackSuccess(mockObject, "callBackSampleRef")

      }

    }
//
//    "ignore should send an email if reminder before closing date" in {
//
//      val emailSenderActor = TestActorRef(
//        Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {}),
//        "email-sender-actor-2"
//      )
//
//      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))
//
////      val mockObject = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate)
//      val reminder = ???
//      val sendDate = ???
//      val htsUserScheduleMsg = HtsUserScheduleMsg(reminder,sendDate )
//      val mockObject = UpdateCallBackSuccess(htsUserScheduleMsg, "callbackRef")
//
//      val requestCaptor = ArgumentCaptor.forClass(classOf[SendTemplatedEmailRequest])
//
//      when(
//        httpClient.POST[SendTemplatedEmailRequest, HttpResponse](
//          anyString,
//          requestCaptor.capture(),
//          any[Seq[(String, String)]]
//        )(any(), any(), any[HeaderCarrier], any[ExecutionContext])
//      ).thenReturn(Future.successful(HttpResponse(202, "")))
//
//      when(mockRepository.updateNextSendDate(any(), any()))
//        .thenReturn(Future.successful(true))
//
//      when(mockRepository.updateCallBackRef(any(), any()))
//        .thenReturn(Future.successful(true))
//
//      within(5 seconds) {
//
//        emailSenderActor ! mockObject
//
//        // if should send and email.
//        // htsUserUpdateActor ! updatedReminder
//
//      }
//
//    }

  }

}
