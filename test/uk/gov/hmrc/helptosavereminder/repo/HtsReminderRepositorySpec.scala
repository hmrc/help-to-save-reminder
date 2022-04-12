/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavereminder.repo

import org.scalamock.scalatest.MockFactory
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.models.HtsUserSchedule
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success}

class HtsReminderRepositorySpec extends BaseSpec with MongoSpecSupport with MockFactory {

  val env = mock[play.api.Environment]

  override val servicesConfig = mock[ServicesConfig]

  implicit val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector = mongoConnectorForTest
  }

  val htsReminderMongoRepository = new HtsReminderMongoRepository(reactiveMongoComponent)

  "Calls to create Reminder a HtsReminder repository" should {
    "should successfully create that reminder" in {

      val reminderValue = ReminderGenerator.nextReminder

      val result: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(reminderValue)

      result.futureValue shouldBe true

      val callBackRef = System.currentTimeMillis().toString + reminderValue.nino

      val nextSendDate: Future[Boolean] =
        htsReminderMongoRepository.updateCallBackRef(reminderValue.nino.value, callBackRef)

      nextSendDate.futureValue shouldBe true
    }

    "should not update the userSchedule if daysToReceive field is Empty" in {

      val reminderValue = ReminderGenerator.nextReminder
      val result: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue.copy(daysToReceive = Seq.empty))
      result.futureValue shouldBe false
    }
  }

  "Calls to findHtsUsersToProcess a HtsReminder repository" should {

    val reminderValue = (ReminderGenerator.nextReminder)
    val result: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(reminderValue)
    result.futureValue shouldBe true
    val now = LocalDate.now
    htsReminderMongoRepository.updateNextSendDate(reminderValue.nino.value, LocalDate.now())
    htsReminderMongoRepository.updateEndDate(reminderValue.nino.value, LocalDate.now())

    "should successfully find that user" in {

      val usersToProcess: Future[Option[List[HtsUserSchedule]]] = htsReminderMongoRepository.findHtsUsersToProcess()

      usersToProcess.futureValue match {
        case Some(list) => {
          list.size shouldBe >=(1)
          list.map(s => s.nextSendDate.isAfter(now) shouldBe false)
        }
        case None =>
      }
    }

    "should fail find that user" in {
      val usersToProcess: Future[Option[List[HtsUserSchedule]]] = htsReminderMongoRepository.findHtsUsersToProcess()

      usersToProcess.futureValue match {
        case Some(list) => {
          list.size shouldBe >=(1)
          list.map(s => s.nextSendDate.isAfter(now) shouldBe false)
        }
        case None =>
      }
    }
  }

  "Calls to updateNextSendDate a Hts Reminder repository" should {

    "should successfully update NextSendDate " in {

      val reminderValue = ReminderGenerator.nextReminder

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue)

      updateStatus.futureValue shouldBe true

      val nextSendDate: Future[Boolean] =
        htsReminderMongoRepository.updateNextSendDate(reminderValue.nino.value, LocalDate.now())

      nextSendDate.futureValue shouldBe true

    }

    "should successfully update endDate field" in {

      val reminderValue = ReminderGenerator.nextReminder

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue)

      updateStatus.futureValue shouldBe true

      val endDate: Future[Boolean] =
        htsReminderMongoRepository.updateEndDate(reminderValue.nino.value, LocalDate.now())

      endDate.futureValue shouldBe true

    }

  }

  "Calls to updateCallBackRef a Hts Reminder repository" should {
    "should successfully update CallBackRef " in {

      val reminderValue = ReminderGenerator.nextReminder
      val callBackRef = System.currentTimeMillis().toString + reminderValue.nino

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue)

      updateStatus.futureValue shouldBe true

      val nextSendDate: Future[Boolean] =
        htsReminderMongoRepository.updateCallBackRef(reminderValue.nino.value, callBackRef)

      nextSendDate.futureValue shouldBe true

    }
  }

  "Calls to updateReminderUser on Hts Reminder repository" should {
    "should successfully update the user " in {

      val reminderValue = ReminderGenerator.nextReminder

      val modifiedReminder =
        reminderValue.copy(nino = Nino("RL256540A"), email = "raomohan2012@yahoo.com", optInStatus = false)

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(modifiedReminder)

      updateStatus.futureValue shouldBe true

      val callBackRef = System.currentTimeMillis().toString + reminderValue.nino

      val nextSendDate: Future[Boolean] =
        htsReminderMongoRepository.updateCallBackRef(modifiedReminder.nino.value, callBackRef)

      nextSendDate.futureValue shouldBe true

    }
  }

  "Calls to updateReminderUser on Hts Reminder repository" should {
    "should successfully create the user if update fails " in {

      val reminderValue = ReminderGenerator.nextReminder

      val modifiedReminder =
        reminderValue.copy(email = "raomohan2012@yahoo.com", optInStatus = true)

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(modifiedReminder)

      updateStatus.futureValue shouldBe true

      val callBackRef = System.currentTimeMillis().toString + reminderValue.nino

      val nextSendDate: Future[Boolean] =
        htsReminderMongoRepository.updateCallBackRef(modifiedReminder.nino.value, callBackRef)

      nextSendDate.futureValue shouldBe true

    }
  }

  "Calls to findByNino on Hts Reminder repository" should {
    "should successfully find the user " in {

      val reminderValue = ReminderGenerator.nextReminder

      val result: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue.copy(nino = Nino("SK798383D")))

      result onComplete {
        case Success(_) =>
          val htsUserOption = htsReminderMongoRepository.findByNino("SK798383D")

          htsUserOption.futureValue.get.nino.value shouldBe "SK798383D"
        case Failure(exception) => new Exception(s"Attempt at finding user by their nino failed because: $exception")
      }

    }
  }

  "Calls to findByCallBackRef on Hts Reminder repository" should {
    "should successfully find the user " in {

      val callBackRef = UUID.randomUUID().toString

      val reminderValue = ReminderGenerator.nextReminder.copy(callBackUrlRef = callBackRef)

      val result: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue.copy(nino = Nino("SK798383D")))

      result onComplete {
        case Success(_) => {
          val htsUserOption = htsReminderMongoRepository.findByCallBackUrlRef(callBackRef)
          htsUserOption.futureValue.get.nino.value shouldBe "SK798383D"
        }
        case Failure(e) => {
          throw new Exception(s"Failed to update user because: $e")
        }
      }

    }
  }

  "Calls to deleteHtsUser on Hts Reminder repository" should {
    "should not successfully delete the user " in {

      val reminderValue = ReminderGenerator.nextReminder

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue)

      updateStatus.futureValue shouldBe true

      val result =
        htsReminderMongoRepository.deleteHtsUser("AE373528D")

      result.futureValue shouldBe Left("Could not find htsUser to delete")

    }

    "should successfully delete the user " in {

      val reminderValue = ReminderGenerator.nextReminder

      val updateStatus: Future[Boolean] =
        htsReminderMongoRepository.updateReminderUser(reminderValue)

      updateStatus.futureValue shouldBe true

      val result =
        htsReminderMongoRepository.deleteHtsUser(reminderValue.nino.toString())

      result.futureValue shouldBe Right(())

    }
  }

  "Calls to deleteHtsUserByCallBack on Hts Reminder repository" should {
    "should successfully delete the user " in {
      val callBackRef = System.currentTimeMillis().toString + "SK798383D"

      val nextSendDate: Future[Boolean] =
        htsReminderMongoRepository.updateCallBackRef("SK798383D", callBackRef)

      nextSendDate.futureValue shouldBe true

      val result =
        htsReminderMongoRepository.deleteHtsUserByCallBack("SK798383D", callBackRef)

      result.futureValue shouldBe Right(())

    }
    "should not successfully delete the user " in {
      val callBackRef = System.currentTimeMillis().toString + "SK798384A"

      val result =
        htsReminderMongoRepository.deleteHtsUserByCallBack("SK798384B", callBackRef)

      result.futureValue shouldBe Left("Could not find htsUser to delete by callBackUrlRef")

    }
  }

  "Calls to updateEmail on Hts Reminder repository" should {
    "should successfully update the users email " in {

      val updateStatus: Future[Int] =
        htsReminderMongoRepository.updateEmail("SK798383D", "James", "Tinder", "modifiedReminder@test.com")

      updateStatus.futureValue shouldBe 404

    }
  }

}
