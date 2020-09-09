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

package uk.gov.hmrc.helptosavereminder.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit._
import com.kenshoo.play.metrics.PlayModule
import org.mockito.ArgumentCaptor
import uk.gov.hmrc.helptosavereminder.models.{HtsReminderTemplate, SendTemplatedEmailRequest, UpdateCallBackSuccess}
import play.api.{Application, Mode}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class EmailSenderActorSpec
    extends TestKit(ActorSystem("TestProcessingSystem")) with UnitSpec with MockitoSugar with GuiceOneAppPerSuite
    with BeforeAndAfterAll with DefaultTimeout with ImplicitSender {

  def additionalConfiguration: Map[String, String] =
    Map(
      "logger.application" -> "ERROR",
      "logger.play"        -> "ERROR",
      "logger.root"        -> "ERROR",
      "org.apache.logging" -> "ERROR",
      "com.codahale"       -> "ERROR")
  private val bindModules: Seq[GuiceableModule] = Seq(new PlayModule)

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .bindings(bindModules: _*)
    .in(Mode.Test)
    .build()

  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val mockLockRepo = mock[LockRepository]

  val httpClient = mock[HttpClient]

  val env = mock[play.api.Environment]

  val servicesConfig = mock[ServicesConfig]

  val emailConnector = mock[EmailConnector]

  val mongoApi = app.injector.instanceOf[play.modules.reactivemongo.ReactiveMongoComponent]

  lazy val mockRepository = mock[HtsReminderMongoRepository]

  override def beforeAll =
    when(mockLockRepo lock (anyString, anyString, any())) thenReturn true

  //override def afterAll: Unit =
  //  shutdown()

  "Email Sender Actor" must {

    "should send an Hts object to DB for saving" in {

      val htsUserUpdateActorProbe = TestProbe()

      val emailSenderActor = TestActorRef(
        Props(new EmailSenderActor(servicesConfig, mockRepository, emailConnector) {
          //override lazy val repository = mockRepository
          //override val lockrepo = mockLockRepo
        }),
        "email-sender-actor"
      )

      val mockObject = ReminderGenerator.nextReminder

      val mockResponse = HttpResponse(responseStatus = 202)

      val template = HtsReminderTemplate("joe@bloggs.com", "upload-ref", "calBakcUrlRef")
      val requestCaptor = ArgumentCaptor.forClass(classOf[SendTemplatedEmailRequest])

      when(
        httpClient.POST[SendTemplatedEmailRequest, HttpResponse](
          anyString,
          requestCaptor.capture(),
          any[Seq[(String, String)]])(any(), any(), any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(202)))

      when(mockRepository.updateNextSendDate(any(), any()))
        .thenReturn(Future.successful(true))

      when(mockRepository.updateCallBackRef(any(), any()))
        .thenReturn(Future.successful(true))

      within(5 seconds) {

        emailSenderActor ! mockObject

        emailSenderActor ! UpdateCallBackSuccess(mockObject, "callBackSampleRef")
        //htsUserUpdateActorProbe.expectMsg(mockObject)

      }

    }

  }

}
