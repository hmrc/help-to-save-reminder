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
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.HtsUserScheduleMsg
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{LockRepository, MongoLockRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ProcessingSupervisorSpec(implicit ec: ExecutionContext)
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with MockitoSugar {

  val mockLockRepo = mock[LockRepository]

  val httpClient = mock[HttpClient]

  val env = mock[play.api.Environment]

  override val servicesConfig = mock[ServicesConfig]

  val emailConnector = mock[EmailConnector]

  val lockRepo = mock[MongoLockRepository]

  val mongoApi = app.injector.instanceOf[MongoComponent]

  lazy val mockRepository = mock[HtsReminderMongoRepository]

  override def beforeAll =
    when(mockLockRepo takeLock (anyString, anyString, any())) thenReturn Future.successful(true)

  //override def afterAll: Unit =
  //  shutdown()

  "processing supervisor" must {

    "send request to start with no requests queued" in {

      val emailSenderActorProbe = TestProbe()

      val processingSupervisor = TestActorRef(
        Props(new ProcessingSupervisor(mongoApi, servicesConfig, emailConnector, lockRepo) {
          override lazy val emailSenderActor = emailSenderActorProbe.ref
          override lazy val repository = mockRepository
        }),
        "process-supervisor1"
      )

      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))

      val mockObject = HtsUserScheduleMsg(ReminderGenerator.nextReminder, currentDate)

      when(mockRepository.findHtsUsersToProcess())
        .thenReturn(Future.successful(Some(List(mockObject.htsUserSchedule))))

      within(5 seconds) {

        //emailSenderActorProbe.reply("SUCCESS")
        processingSupervisor ! "START"
        emailSenderActorProbe.expectMsg(mockObject)
        emailSenderActorProbe.reply("SUCCESS")

        processingSupervisor ! "STOP" // simulate stop coming from calc requestor

      }

    }

  }

}
