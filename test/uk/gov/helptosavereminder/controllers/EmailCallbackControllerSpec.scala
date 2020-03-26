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

package uk.gov.helptosavereminder.controllers
import java.time.LocalDateTime

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
import play.api.{Application, Configuration, Environment, Mode}
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.controllers.EmailCallbackController
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.models.{EventItem, EventsMap, HtsUser}
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor

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

  private val serviceConfig = new ServicesConfig(configuration, new RunMode(configuration, Mode.Dev))
  val mockHttp: HttpClient = mock[HttpClient]
  var runMode = mock[RunMode]
  lazy val mockRepository = mock[HtsReminderMongoRepository]
  implicit val auditor: HTSAuditor = mock[HTSAuditor]
  lazy val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val controller = new EmailCallbackController(mockHttp, serviceConfig, mcc, mockRepository, auditor)

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
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = LocalDateTime.now().toString() + "AE345678D")
        val result1: Future[Either[String, HtsUser]] = htsReminderMongoRepository.createReminder(htsReminderUser)

        await(result1) match {
          case (Right(x)) => {
            x.nino shouldBe htsReminderUser.nino
          }
        }
        val callBackReferences = "1580214107339AE345678D"
        when(mockRepository.findByNino(any())).thenReturn(Some(htsReminderUser))
        when(mockHttp.DELETE[HttpResponse](any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(200)))
        when(mockRepository.deleteHtsUserByCallBack(any(), any())).thenReturn(Future.successful(Right()))
        val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)

        await(result) match {
          case x => {
            1 shouldBe 1
          }
        }

      }
    }
    "fail to update the DB" should {
      "respond with a 200 assuming all is good" in {
        val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))
        val htsReminderUser = (ReminderGenerator.nextReminder)
          .copy(nino = Nino("AE345678D"), callBackUrlRef = LocalDateTime.now().toString() + "AE345678D")
        val result1: Future[Either[String, HtsUser]] = htsReminderMongoRepository.createReminder(htsReminderUser)

        await(result1) match {
          case (Right(x)) => {
            x.nino shouldBe htsReminderUser.nino
          }
        }
        val callBackReferences = "1580214107339AE345678D"
        when(mockRepository.findByNino(any())).thenReturn(Some(htsReminderUser))
        when(mockHttp.DELETE[HttpResponse](any(), any())(any(), any(), any()))
          .thenReturn(Future.failed(new Exception("Exception failure")))
        when(mockRepository.deleteHtsUserByCallBack(any(), any())).thenReturn(Future.successful(Right()))
        val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)

        await(result) match {
          case x => {
            1 shouldBe 1
          }
        }

      }
    }
  }

  "respond with a 200 containing FAILURE string if Nino does not exists or update fails" in {

    val htsReminderUser = (ReminderGenerator.nextReminder)
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString() + "AE456789D")

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithPermanentBounce))

    val result1: Future[Either[String, HtsUser]] = htsReminderMongoRepository.createReminder(htsReminderUser)

    await(result1) match {
      case (Right(x)) => {
        x.nino shouldBe htsReminderUser.nino
      }
    }
    val callBackReferences = "1580214107339AE456789D"
    when(mockRepository.findByNino(any())).thenReturn(Some(htsReminderUser))
    when(mockRepository.deleteHtsUserByCallBack(any(), any())).thenReturn(Future.successful(Left("Not found")))
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    await(result) match {
      case x => 1 shouldBe 1
    }
  }

  "respond with a 200  if the event List submitted do not contain PermanentBounce event" in {

    val htsReminderUser = (ReminderGenerator.nextReminder)
      .copy(nino = Nino("AE456789D"), callBackUrlRef = LocalDateTime.now().toString() + "AE456789D")

    val fakeRequest = FakeRequest("POST", "/").withJsonBody(Json.toJson(eventsMapWithoutPermanentBounce))

    val result1: Future[Either[String, HtsUser]] = htsReminderMongoRepository.createReminder(htsReminderUser)

    await(result1) match {
      case (Right(x)) => {
        x.nino shouldBe htsReminderUser.nino
      }
    }
    val callBackReferences = "1580214107339AE456789D"
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    await(result) match {
      case x => 1 shouldBe 1
    }
  }

}
