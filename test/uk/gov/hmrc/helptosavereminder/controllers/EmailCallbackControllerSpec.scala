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

package uk.gov.hmrc.helptosavereminder.controllers

import akka.actor.ActorSystem
import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{EventItem, EventsMap}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

class EmailCallbackControllerSpec extends BaseSpec with MongoSupport with IdiomaticMockito {

  implicit val mongo: MongoComponent = mongoComponent

  val htsReminderMongoRepository = new HtsReminderMongoRepository(mongo)

  implicit val sys: ActorSystem = ActorSystem("MyTest")

  private val serviceConfig = new ServicesConfig(configuration)

  val mockHttp: HttpClient = mock[HttpClient]
  private lazy val mockRepository = mock[HtsReminderMongoRepository]
  private lazy val mockEmailConnector = mock[EmailConnector]
  implicit val auditor: HTSAuditor = mock[HTSAuditor]
  lazy val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val controller =
    new EmailCallbackController(serviceConfig, mcc, mockRepository, auditor, mockEmailConnector)

  val eventItem1: EventItem = EventItem("PermanentBounce", LocalDateTime.now())
  val eventItem2: EventItem = EventItem("Opened", LocalDateTime.now())
  val eventItem3: EventItem = EventItem("Delivered", LocalDateTime.now())

  val eventItemList: List[EventItem] = List(eventItem1, eventItem2)

  val eventsMapWithPermanentBounce: EventsMap = EventsMap(eventItemList)
  val eventsMapWithoutPermanentBounce: EventsMap = EventsMap(List(eventItem2, eventItem3))

  "The EmailCallbackController" should {
    "be able to increment a bounce count and" should {
      "respond with a 200 when all is good" in {
        val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))
        val callBackUrlRef = UUID.randomUUID().toString
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = callBackUrlRef)
        val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

        result1.futureValue shouldBe true

        mockRepository.findByCallBackUrlRef(*).returns(Future.successful(Some(htsReminderUser)))
        mockEmailConnector
          .unBlockEmail(*)(*, *)
          .returns(Future.successful(true))
        mockRepository.deleteHtsUserByCallBack(*, *).returns(Future.successful(Right(())))
        val result = controller.handleCallBack(callBackUrlRef).apply(fakeRequest)

        result.futureValue.header.status shouldBe 200
      }

      "respond with a 200 when its unable to block email" in {
        val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))
        val callBackUrlRef = UUID.randomUUID().toString
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = callBackUrlRef)
        val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

        result1.futureValue shouldBe true

        mockRepository.findByCallBackUrlRef(*).returns(Future.successful(Some(htsReminderUser)))
        mockEmailConnector
          .unBlockEmail(*)(*, *)
          .returns(Future.successful(false))
        mockRepository.deleteHtsUserByCallBack(*, *).returns(Future.successful(Right(())))
        val result = controller.handleCallBack(callBackUrlRef).apply(fakeRequest)

        result.futureValue.header.status shouldBe 200
      }
    }

    "fail to update the DB" should {
      "respond with a 200 assuming all is good" in {
        val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))
        val callBackReferences = UUID.randomUUID().toString
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = callBackReferences)
        val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

        result1.futureValue shouldBe true

        mockRepository.findByCallBackUrlRef(*).returns(Future.successful(Some(htsReminderUser)))
        mockEmailConnector
          .unBlockEmail(*)(*, *)
          .returns(Future.failed(new Exception("Exception failure")))
        mockRepository.deleteHtsUserByCallBack(*, *).returns(Future.successful(Right(())))
        val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)

        result.futureValue.header.status shouldBe 200
      }
    }
  }

  "respond with a 200 containing FAILURE string if Nino does not exists or update fails" in {
    val callBackReferences = UUID.randomUUID().toString

    val htsReminderUser = ReminderGenerator.nextReminder
      .copy(nino = Nino("AE456789B"), callBackUrlRef = callBackReferences)

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    result1.futureValue shouldBe true

    mockRepository.findByCallBackUrlRef(*).returns(Future.successful(Some(htsReminderUser)))
    mockRepository
      .deleteHtsUserByCallBack(*, *)
      .returns(Future.successful(Left("Error deleting")))
    mockHttp
      .DELETE[HttpResponse](*, *)(*, *, *)
      .returns(Future.failed(new Exception("Exception failure")))
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    result.futureValue.header.status shouldBe 200
  }

  "respond with a 200  if the event List submitted do not contain PermanentBounce event" in {
    val htsReminderUser = ReminderGenerator.nextReminder
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString + "AE456789D")

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithoutPermanentBounce))

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    result1.futureValue shouldBe true

    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    result.futureValue.header.status shouldBe 200
  }

  "respond with a 400  if the event List submitted do not contain PermanentBounce event" in {
    val htsReminderUser = ReminderGenerator.nextReminder
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString + "AE456789D")

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson("Not a Valid Input"))

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    result1.futureValue shouldBe true

    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    result.futureValue.header.status shouldBe 400
  }

  "send back error response if the request do not contain Json body in deleteUser request" in {
    val htsReminderUser = ReminderGenerator.nextReminder
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString + "AE456789D")

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    result1.futureValue shouldBe true

    val fakeRequest = FakeRequest("POST", "/")

    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)

    result.futureValue.header.status shouldBe 400
  }
}
