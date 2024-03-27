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
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually.eventually
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.models.{ActorUtils, HtsUserScheduleMsg}
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, TimePeriodLockService}

import java.time.{LocalDate, ZoneId}
import scala.concurrent.Future
import scala.language.postfixOps

class ProcessingSupervisorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with BaseSpec with DefaultTimeout with ImplicitSender
    with IdiomaticMockito {

  "processing supervisor" must {
    "send request to start with no requests queued" in {
      val emailConnector = mock[EmailConnector]
      val lockRepo = mock[MongoLockRepository]
      val mongoApi = app.injector.instanceOf[MongoComponent]
      val mockRepository = mock[HtsReminderMongoRepository]
      val emailSenderActorProbe = TestProbe()
      val processingSupervisor = TestActorRef(
        Props(new ProcessingSupervisor(mongoApi, servicesConfig, emailConnector, lockRepo) {
          override lazy val emailSenderActor: ActorRef = emailSenderActorProbe.ref
          override lazy val repository: HtsReminderMongoRepository = mockRepository
          override val lockKeeper: TimePeriodLockService = mock[TimePeriodLockService]
          when(lockKeeper.withRenewedLock(*)(*)).thenAnswer(_.getArguments.head.asInstanceOf[Future[Unit]])
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
