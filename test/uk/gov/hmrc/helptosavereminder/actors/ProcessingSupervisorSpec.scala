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
import play.api.{Application, Configuration, Mode}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.{Application, Mode}
import uk.gov.hmrc.helptosavereminder.actors.ProcessingSupervisor
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ReminderSchedulerSpec
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

  "processing supervisor" must {

    "send request to start with no requests queued" in {

      val emailSenderActorProbe = TestProbe()

      val processingSupervisor = TestActorRef(
        Props(new ProcessingSupervisor(mongoApi, servicesConfig, emailConnector) {
          override lazy val emailSenderActor = emailSenderActorProbe.ref
          override lazy val repository = mockRepository
          override val lockrepo = mockLockRepo
        }),
        "process-supervisor1"
      )

      val mockObject = ReminderGenerator.nextReminder

      when(mockRepository.findByNino("AT787806D"))
        .thenReturn(Future.successful(Some(mockObject)))

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
