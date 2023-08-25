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
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{HtsUserScheduleMsg, SendTemplatedEmailRequest, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.lock.LockRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import scala.concurrent.Future
import scala.concurrent.duration._
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
      val emailSenderActor = TestActorRef(
        Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {}),
        "email-sender-actor"
      )

      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))

      val mockObject = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate)

      val requestCaptor = ArgumentCaptor.forClass(classOf[SendTemplatedEmailRequest])

      httpClient
        .POST[SendTemplatedEmailRequest, HttpResponse](*, requestCaptor.capture(), *)(*, *, *, *)
        .returns(Future.successful(HttpResponse(202, "")))

      mockRepository.updateNextSendDate(*, *).returns(Future.successful(true))

      mockRepository.updateCallBackRef(*, *).returns(Future.successful(true))

      within(5 seconds) {

        emailSenderActor ! mockObject

        emailSenderActor ! UpdateCallBackSuccess(mockObject, "callBackSampleRef")

      }

    }

  }

}
