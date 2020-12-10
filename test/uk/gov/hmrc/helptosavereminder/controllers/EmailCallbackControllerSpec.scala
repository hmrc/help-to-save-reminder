/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.kenshoo.play.metrics.PlayModule
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.{Application, Configuration, Environment, Mode}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{EventItem, EventsMap}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class EmailCallbackControllerSpec extends UnitSpec with MongoSpecSupport with GuiceOneAppPerSuite with MockitoSugar {
  def additionalConfiguration: Map[String, String] =
    Map(
      "logger.application" -> "ERROR",
      "logger.play"        -> "ERROR",
      "logger.root"        -> "ERROR",
      "org.apache.logging" -> "ERROR",
      "com.codahale"       -> "ERROR")

  private val bindModules: Seq[GuiceableModule] = Seq(new PlayModule)

  implicit val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector = mongoConnectorForTest
  }

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .bindings(bindModules: _*)
    .in(Mode.Test)
    .build()
  val htsReminderMongoRepository = new HtsReminderMongoRepository(reactiveMongoComponent)

  implicit val sys = ActorSystem("MyTest")
  implicit val mat = ActorMaterializer()

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration)
  val mockHttp: HttpClient = mock[HttpClient]
  lazy val mockRepository = mock[HtsReminderMongoRepository]
  lazy val mockEmailConnector = mock[EmailConnector]
  implicit val auditor: HTSAuditor = mock[HTSAuditor]
  lazy val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
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

        await(result1) match {
          case x => {
            x shouldBe true
          }
        }
        when(mockRepository.findByCallBackUrlRef(any())).thenReturn(Some(htsReminderUser))
        when(mockEmailConnector.unBlockEmail(any())(any(), any()))
          .thenReturn(Future.successful(true))
        when(mockRepository.deleteHtsUserByCallBack(any(), any())).thenReturn(Future.successful(Right(())))
        val result = controller.handleCallBack(callBackUrlRef).apply(fakeRequest)

        await(result) match {
          case x => {
            status(x) shouldBe 200
          }
        }

      }

      "respond with a 200 when its unable to block email" in {
        val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))
        val callBackUrlRef = UUID.randomUUID().toString
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = callBackUrlRef)
        val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

        await(result1) match {
          case x => {
            x shouldBe true
          }
        }
        when(mockRepository.findByCallBackUrlRef(any())).thenReturn(Some(htsReminderUser))
        when(mockEmailConnector.unBlockEmail(any())(any(), any()))
          .thenReturn(Future.successful(false))
        when(mockRepository.deleteHtsUserByCallBack(any(), any())).thenReturn(Future.successful(Right(())))
        val result = controller.handleCallBack(callBackUrlRef).apply(fakeRequest)

        await(result) match {
          case x => {
            status(x) shouldBe 200
          }
        }

      }
    }
    "fail to update the DB" should {
      "respond with a 200 assuming all is good" in {
        val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))
        val callBackReferences = UUID.randomUUID().toString
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = callBackReferences)
        val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

        await(result1) match {
          case x => {
            x shouldBe true
          }
        }
        when(mockRepository.findByCallBackUrlRef(any())).thenReturn(Some(htsReminderUser))
        when(mockEmailConnector.unBlockEmail(any())(any(), any()))
          .thenReturn(Future.failed(new Exception("Exception failure")))
        when(mockRepository.deleteHtsUserByCallBack(any(), any())).thenReturn(Future.successful(Right(())))
        val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)

        await(result) match {
          case x => {
            status(x) shouldBe 200
          }
        }

      }
    }
  }

  "respond with a 200 containing FAILURE string if Nino does not exists or update fails" in {

    val callBackReferences = UUID.randomUUID().toString

    val htsReminderUser = ReminderGenerator.nextReminder
      .copy(nino = Nino("AE456789B"), callBackUrlRef = callBackReferences)

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    await(result1) match {
      case x => {
        x shouldBe true
      }
    }

    when(mockRepository.findByCallBackUrlRef(any())).thenReturn(Some(htsReminderUser))
    when(mockRepository.deleteHtsUserByCallBack(any(), any()))
      .thenReturn(Future.successful(Left("Error deleting")))
    when(mockHttp.DELETE[HttpResponse](any(), any())(any(), any(), any()))
      .thenReturn(Future.failed(new Exception("Exception failure")))
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    await(result) match {
      case x => 1 shouldBe 1
    }
  }

  "respond with a 200  if the event List submitted do not contain PermanentBounce event" in {

    val htsReminderUser = (ReminderGenerator.nextReminder)
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString() + "AE456789D")

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithoutPermanentBounce))

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    await(result1) match {
      case x => {
        x shouldBe true
      }
    }
    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    await(result) match {
      case x => status(x) shouldBe 200
    }
  }

  "respond with a 400  if the event List submitted do not contain PermanentBounce event" in {

    val htsReminderUser = (ReminderGenerator.nextReminder)
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString() + "AE456789D")

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson("Not a Valid Input"))

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    await(result1) match {
      case x => {
        x shouldBe true
      }
    }
    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    await(result) match {
      case x => status(x) shouldBe 400
    }
  }

  "send back error response if the request do not contain Json body in deleteUser request" in {

    val htsReminderUser = (ReminderGenerator.nextReminder)
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString() + "AE456789D")

    val result1: Future[Boolean] = htsReminderMongoRepository.updateReminderUser(htsReminderUser)

    await(result1) match {
      case x => {
        x shouldBe true
      }
    }

    val fakeRequest = FakeRequest("POST", "/")

    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)

    await(result) match {
      case x => status(x) shouldBe 400
    }

  }

}
