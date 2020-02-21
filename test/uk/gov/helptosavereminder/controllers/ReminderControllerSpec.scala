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

import com.kenshoo.play.metrics.PlayModule
import uk.gov.hmrc.domain.Nino
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}
import uk.gov.hmrc.helptosavereminder.controllers.HtsUserUpdateController
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderRepository
import play.api.test._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavereminder.models.{CancelHtsUserReminder, HtsUser, UpdateEmail}
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.play.bootstrap.config.RunMode

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino => v2Nino}
import uk.gov.hmrc.helptosave.controllers.HtsReminderAuth._

import scala.util.{Failure, Success}

class HtsUserUpdateControllerSpec extends AuthSupport {
  def additionalConfiguration: Map[String, String] =
    Map(
      "logger.application" -> "ERROR",
      "logger.play"        -> "ERROR",
      "logger.root"        -> "ERROR",
      "org.apache.logging" -> "ERROR",
      "com.codahale"       -> "ERROR")

  private val bindModules: Seq[GuiceableModule] = Seq(new PlayModule)

  val mockRepository = mock[HtsReminderRepository]

  val mcc: ControllerComponents = fakeApplication.injector.instanceOf[ControllerComponents]

  def mockUpdateRepository(htsUser: HtsUser)(result: Boolean): Unit =
    (mockRepository
      .updateReminderUser(_: HtsUser))
      .expects(htsUser)
      .returning(result)

  def mockCancelRepository(nino: String)(result: Either[String, Unit]): Unit =
    (mockRepository
      .deleteHtsUser(_: String))
      .expects(nino)
      .returning(result)

  def mockGetRepository(nino: String)(result: Option[HtsUser]): Unit =
    (mockRepository
      .findByNino(_: String))
      .expects(nino)
      .returning(result)

  def mockUpdateEmailRepository(nino: String, email: String)(result: Boolean): Unit =
    (mockRepository
      .updateEmail(_: String, _: String))
      .expects(nino, email)
      .returning(result)

  val fakeRequest = FakeRequest()

  var runMode = mock[RunMode]

  override val mockAuthConnector: AuthConnector = mock[AuthConnector]

  "The ReminderController " should {
    "be able to return a success if Hts user is correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val submitNino = htsReminderUser.nino
      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-entity").withBody(Json.toJson(htsReminderUser))

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateRepository(htsReminderUser)(true)

      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 200

    }

    "fail to update if the input data for Hts user is not correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))

      val inValidFormData = "Not able to Stringify to HtsUser"
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(inValidFormData)))
      status(result) shouldBe 400

    }

    "be able to return a failure if Hts user is correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val submitNino = htsReminderUser.nino
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateRepository(htsReminderUser)(false)

      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 304

    }

    "be able to return a failure if input Hts user is not successfully casted to HtsUser object" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val fakeRequest = FakeRequest("POST", "/") //.withBody(Json.toJson(htsReminderUser))
      val invalidFormData = "Not able to cast to HtsUser object"

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(invalidFormData)))
      status(result) shouldBe 400

    }

    "be able to successfully delete an HtsUser" in {

      val cancelHtsUser = CancelHtsUserReminder.apply("AE123456C")

      val jsonRequest = Json.toJson(cancelHtsUser)
      //val convertedObject: CancelHtsUserReminder = Json.fromJson(jsonRequest).get
      //val convertedNino = convertedObject.nino
      val tumri = Json.parse(Json.stringify(jsonRequest)).validate[CancelHtsUserReminder] onComplete ({
        case Success(value) => value.get.nino
        case Failure(value) =>
      })

      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockCancelRepository("AE123456C")(Right())

      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(cancelHtsUser)))
      status(result) shouldBe 200

    }

    "return NotModified status if there is an error while storing in database" in {

      val cancelHtsUser = CancelHtsUserReminder("AE123456C")
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockCancelRepository("AE123456C")(Left("error occurred while storing in DB"))

      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(cancelHtsUser)))
      status(result) shouldBe 304

    }

    "be able to return successfully HtsUser if user Nino exists in DB" in {

      val fakeRequest = FakeRequest("GET", "/")
      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockGetRepository("AE123456C")(Some(htsReminderUser))
      }

      val result = controller.getIfHtsUserExists("AE123456C")(fakeRequest)
      status(result) shouldBe 200

    }

    "return NotFound status if user with Nino does not exist in DB" in {

      val fakeRequest = FakeRequest("GET", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockGetRepository("AE123456C")(None)
      }

      val result = controller.getIfHtsUserExists("AE123456C")(fakeRequest)
      status(result) shouldBe 404

    }

    "be able to return a success if Hts users details for email change are correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456D"))
      val updateEmailInput = UpdateEmail(htsReminderUser.nino, htsReminderUser.email)
      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-email").withBody(Json.toJson(updateEmailInput))

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateEmailRepository(updateEmailInput.nino.nino, updateEmailInput.email)(true)

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 200

    }

    "be able to return a success with Not Found if Hts users details for email change are correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456D"))
      val updateEmailInput = UpdateEmail(htsReminderUser.nino, htsReminderUser.email)
      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-email").withBody(Json.toJson(updateEmailInput))

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateEmailRepository(updateEmailInput.nino.nino, updateEmailInput.email)(false)

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 404

    }

    "return a Bad request response if Hts users details for email change are in-correct" in {

      val inValidFormData = "Not able to Stringify to HtsUser"
      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-email").withBody(Json.toJson(inValidFormData))

      val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(inValidFormData)))
      status(result) shouldBe 400

    }

  }
}
