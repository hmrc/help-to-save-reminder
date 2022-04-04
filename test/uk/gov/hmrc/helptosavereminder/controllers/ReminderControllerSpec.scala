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

package uk.gov.hmrc.helptosavereminder.controllers

import play.api.http.Status._
import play.api.libs.json.{JsSuccess, Json}
import play.api.mvc.Result
import play.api.test._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino => v2Nino}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.auth.HtsReminderAuth._
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.models.{CancelHtsUserReminder, HTSEvent, HtsUserSchedule, UpdateEmail}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderRepository

import scala.concurrent.{ExecutionContext, Future}

class ReminderControllerSpec extends AuthSupport {
  val mockRepository = mock[HtsReminderRepository]

  def mockSendAuditEvent(event: HTSEvent) =
    (auditor
      .sendEvent(_: HTSEvent)(_: ExecutionContext))
      .expects(event, *)
      .returning(())

  def mockUpdateRepository(htsUser: HtsUserSchedule)(result: Boolean): Unit =
    (mockRepository
      .updateReminderUser(_: HtsUserSchedule))
      .expects(htsUser)
      .returning(Future.successful(result))

  def mockCancelRepository(nino: String)(result: Either[String, Unit]): Unit =
    (mockRepository
      .deleteHtsUser(_: String))
      .expects(nino)
      .returning(Future.successful(result))

  def mockGetRepository(nino: String)(result: Option[HtsUserSchedule]): Unit =
    (mockRepository
      .findByNino(_: String))
      .expects(nino)
      .returning(Future.successful(result))

  def mockUpdateEmailRepository(nino: String, firstName: String, lastName: String, email: String)(result: Int): Unit =
    (mockRepository
      .updateEmail(_: String, _: String, _: String, _: String))
      .expects(nino, firstName, lastName, email)
      .returning(Future.successful(result))

  val fakeRequest = FakeRequest()

  override val mockAuthConnector: AuthConnector = mock[AuthConnector]

  implicit val auditor: HTSAuditor = mock[HTSAuditor]

  val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

  "The ReminderController " should {
    "be able to return a success if Hts user is correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateRepository(htsReminderUser)(true)
      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      result.futureValue.header.status shouldBe OK

    }

    "fail to update if the input data for Hts user is not correct" in {

      val inValidFormData = "Not able to Stringify to HtsUser"
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(inValidFormData)))
      result.futureValue.header.status shouldBe 400

    }

    "be able to return a failure if Hts user is correct" in {
      val htsReminderUser = ReminderGenerator.nextReminder.copy(nino = Nino("AE123456C"))
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateRepository(htsReminderUser)(false)

      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      result.futureValue.header.status shouldBe 304

    }

    "send back error response if the request do not contain Json body" in {
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.update()(fakeRequest)
      result.futureValue.header.status shouldBe 400
    }

    "be able to return a failure if input Hts user is not successfully casted to HtsUser object" in {
      val fakeRequest = FakeRequest("POST", "/")
      val invalidFormData = "Not able to cast to HtsUser object"

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(invalidFormData)))
      result.futureValue.header.status shouldBe 400

    }

    "be able to successfully delete an HtsUser" in {

      val cancelHtsUser = CancelHtsUserReminder.apply("AE123456C")

      val jsonRequest = Json.toJson(cancelHtsUser)

      val json = Json.toJson(jsonRequest)
      Json.fromJson[CancelHtsUserReminder](json) shouldBe JsSuccess(cancelHtsUser)

      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockCancelRepository("AE123456C")(Right(()))
      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(cancelHtsUser)))
      result.futureValue.header.status shouldBe 200

    }

    "return NotModified status if there is an error while deleting from database" in {

      val cancelHtsUser = CancelHtsUserReminder("AE123456C")
      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockCancelRepository("AE123456C")(Left("error occurred while storing in DB"))

      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(cancelHtsUser)))
      result.futureValue.header.status shouldBe 304

    }

    "be able to return a failure if input Hts user is not successfully casted to CancelHtsUserReminder object" in {
      val fakeRequest = FakeRequest("POST", "/")
      val invalidFormData = "Not able to cast to CancelHtsUserReminder object"

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(invalidFormData)))
      result.futureValue.header.status shouldBe 400

    }

    "send back error response if the request do not contain Json body in deleteUser request" in {
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

      val result = controller.deleteHtsUser()(fakeRequest)
      result.futureValue.header.status shouldBe 400

    }

    "be able to return successfully HtsUser if user Nino exists in DB" in {

      val fakeRequest = FakeRequest("GET", "/")
      val htsReminderUser = ReminderGenerator.nextReminder.copy(nino = Nino("AE123456C"))

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockGetRepository("AE123456C")(Some(htsReminderUser))
      }

      val result = controller.getHtsUser("AE123456C")(fakeRequest)
      result.futureValue.header.status shouldBe 200

    }

    "return NotFound status if user with Nino does not exist in DB" in {

      val fakeRequest = FakeRequest("GET", "/")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockGetRepository("AE123456C")(None)
      }

      val result = controller.getHtsUser("AE123456C")(fakeRequest)
      result.futureValue.header.status shouldBe 404

    }

    "be able to return a success if Hts users details for email change are correct" in {

      val htsReminderUser = ReminderGenerator.nextReminder.copy(nino = Nino(nino))

      val updateEmailInput =
        UpdateEmail(htsReminderUser.nino, htsReminderUser.firstName, htsReminderUser.lastName, htsReminderUser.email)

      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

        mockUpdateEmailRepository(
          updateEmailInput.nino.value,
          updateEmailInput.firstName,
          updateEmailInput.lastName,
          updateEmailInput.email
        )(OK)
      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      result.futureValue.header.status shouldBe 200
    }

    "be able to return a notModified if Hts users details for email change are correct" in {

      val htsReminderUser = ReminderGenerator.nextReminder.copy(nino = Nino(nino))

      val updateEmailInput =
        UpdateEmail(htsReminderUser.nino, htsReminderUser.firstName, htsReminderUser.lastName, htsReminderUser.email)

      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

        mockUpdateEmailRepository(
          updateEmailInput.nino.value,
          updateEmailInput.firstName,
          updateEmailInput.lastName,
          updateEmailInput.email
        )(NOT_MODIFIED)

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      result.futureValue.header.status shouldBe 304

    }

    "be able to return a success with Not Found if Hts users details for email change are correct" in {

      val htsReminderUser = ReminderGenerator.nextReminder.copy(nino = Nino(nino))

      val updateEmailInput =
        UpdateEmail(htsReminderUser.nino, htsReminderUser.firstName, htsReminderUser.lastName, htsReminderUser.email)

      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

        mockUpdateEmailRepository(
          updateEmailInput.nino.value,
          updateEmailInput.firstName,
          updateEmailInput.lastName,
          updateEmailInput.email
        )(NOT_FOUND)
      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      result.futureValue.header.status shouldBe 404

    }

    "return a Bad request response if Hts users details for email change are in-correct" in {

      val inValidFormData = "Not able to Stringify to HtsUser"
      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(inValidFormData)))
      result.futureValue.header.status shouldBe 400

    }

    "send back error response if the request do not contain Json body in updateEmail request" in {
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, testCC, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.updateEmail()(fakeRequest)
      result.futureValue.header.status shouldBe 400

    }

    "return a Forbidden status" when {
      val nino1 = "AE123456D"
      val htsReminderUser = ReminderGenerator.nextReminder.copy(nino = Nino(nino1))
      def shouldBeForbidden(result: Future[Result]) = result.futureValue.header.status shouldBe 403

      "hitting the getHtsUser endpoint when the nino in the URL is different to the auth supplied nino" in {

        val fakeRequest = FakeRequest("GET", s"/gethtsuser/$nino1")

        inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        }

        val result = controller.getHtsUser(nino1)(fakeRequest)

        shouldBeForbidden(result)
      }

      "hitting the update endpoint when the nino in the request body is different to the auth supplied nino" in {

        val request = FakeRequest("POST", "/update-htsuser-entity").withJsonBody(Json.toJson(htsReminderUser))

        inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        }

        val result = controller.update()(request)

        shouldBeForbidden(result)
      }

      "hitting the update email endpoint when the nino in the request body is different to the auth supplied nino" in {

        val request = FakeRequest("POST", "/update-htsuser-email").withJsonBody(Json.toJson(htsReminderUser))

        inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        }

        val result = controller.updateEmail()(request)

        shouldBeForbidden(result)
      }
    }
  }
}
