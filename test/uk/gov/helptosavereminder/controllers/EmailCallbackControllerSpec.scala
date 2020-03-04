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
import com.kenshoo.play.metrics.PlayModule
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment, Mode}
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.controllers.EmailCallbackController
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import play.api.test._
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration._
class EmailCallbackControllerSpec
    extends UnitSpec with Matchers with ScalaFutures with GuiceOneAppPerSuite with MockitoSugar {
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

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)
  val fakeRequest = FakeRequest()
  private val serviceConfig = new ServicesConfig(configuration, new RunMode(configuration, Mode.Dev))
  val mockHttp: HttpClient = mock[HttpClient]
  var runMode = mock[RunMode]
  lazy val mockRepository = mock[HtsReminderMongoRepository]
  lazy val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
  lazy val controller = new EmailCallbackController(mockHttp, serviceConfig, mcc, mockRepository)

  "The EmailCallbackController" should {
    "be able to increment a bounce count and" should {
      "respond with a 200 when all is good" in {
        val callBackReferences = "1580214107339YT176603C"
        when(mockRepository.deleteHtsUser(any())).thenReturn(Future.successful(Right()))
        val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
        result.onComplete({
          case Success(success) => contentAsString(success) shouldBe SUCCESS
          case _                =>
        })
      }
    }
  }

  "respond with a 200 containing FAILURE string if Nino does not exists or update fails" in {
    val callBackReferences = "1580214107339YT176603C"
    when(mockRepository.deleteHtsUser(any())).thenReturn(Future.successful(Left("Not found")))
    val result = controller.handleCallBack(callBackReferences).apply(fakeRequest)
    result.onComplete({
      case Success(success) => contentAsString(success) shouldBe FAILURE
      case _                =>
    })
  }

}
