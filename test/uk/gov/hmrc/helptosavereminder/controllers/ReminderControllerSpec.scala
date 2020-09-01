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
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}
import uk.gov.hmrc.helptosavereminder.controllers.HtsUserUpdateController
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderRepository
import play.api.test._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavereminder.models.{CancelHtsUserReminder, HTSEvent, HtsReminderUserDeleted, HtsReminderUserDeletedEvent, HtsReminderUserUpdated, HtsReminderUserUpdatedEvent, HtsUser, UpdateEmail}
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator

import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino => v2Nino}
import uk.gov.hmrc.helptosave.controllers.HtsReminderAuth._
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.utils.TestSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class HtsUserUpdateControllerSpec extends AuthSupport with TestSupport {
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

  def mockSendAuditEvent(event: HTSEvent, nino: String) =
    (auditor
      .sendEvent(_: HTSEvent, _: String)(_: ExecutionContext))
      .expects(event, nino, *)
      .returning(())

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

  def mockUpdateEmailRepository(nino: String, firstName: String, lastName: String, email: String)(
    result: Boolean): Unit =
    (mockRepository
      .updateEmail(_: String, _: String, _: String, _: String))
      .expects(nino, firstName, lastName, email)
      .returning(result)

  val fakeRequest = FakeRequest()

  override val mockAuthConnector: AuthConnector = mock[AuthConnector]

  implicit val auditor: HTSAuditor = mock[HTSAuditor]

  val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

  "The ReminderController " should {
    "be able to return a success if Hts user is correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val submitNino = htsReminderUser.nino
      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-entity").withBody(Json.toJson(htsReminderUser))

      val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

      val auditEventObject = HtsReminderUserUpdatedEvent(
        HtsReminderUserUpdated(htsReminderUser.nino.value, Json.toJson(htsReminderUser)),
        "/help-to-save-reminder/update-htsuser-entity")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateRepository(htsReminderUser)(true)
        mockSendAuditEvent(auditEventObject, htsReminderUser.nino.value)

      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 200

    }

    "fail to update if the input data for Hts user is not correct" in {

      val inValidFormData = "Not able to Stringify to HtsUser"
      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

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

      val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockUpdateRepository(htsReminderUser)(false)

      }

      val result = controller.update()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 304

    }

    "send back error response if the request do not contain Json body" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))

      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.update()(fakeRequest)
      status(result) shouldBe 400

    }

    "be able to return a failure if input Hts user is not successfully casted to HtsUser object" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val fakeRequest = FakeRequest("POST", "/") //.withBody(Json.toJson(htsReminderUser))
      val invalidFormData = "Not able to cast to HtsUser object"

      //val controller = new HtsUserUpdateController(mockRepository, mcc, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(invalidFormData)))
      status(result) shouldBe 400

    }

    "be able to successfully delete an HtsUser" in {

      val cancelHtsUser = CancelHtsUserReminder.apply("AE123456C")

      val jsonRequest = Json.toJson(cancelHtsUser)
      val tumri = Json.parse(Json.stringify(jsonRequest)).validate[CancelHtsUserReminder] onComplete ({
        case Success(value) => value.get.nino
        case Failure(value) =>
      })

      val json = Json.toJson(jsonRequest)
      Json.fromJson[CancelHtsUserReminder](json) shouldBe JsSuccess(cancelHtsUser)

      val fakeRequest = FakeRequest("POST", "/")

      val auditEventObject = HtsReminderUserDeletedEvent(
        HtsReminderUserDeleted(cancelHtsUser.nino, Json.toJson(cancelHtsUser)),
        "/help-to-save-reminder/delete-htsuser-entity")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockCancelRepository("AE123456C")(Right())
        mockSendAuditEvent(auditEventObject, cancelHtsUser.nino)
      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(cancelHtsUser)))
      status(result) shouldBe 200

    }

    "return NotModified status if there is an error while deleting from database" in {

      val cancelHtsUser = CancelHtsUserReminder("AE123456C")
      val fakeRequest = FakeRequest("POST", "/")

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockCancelRepository("AE123456C")(Left("error occurred while storing in DB"))

      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(cancelHtsUser)))
      status(result) shouldBe 304

    }

    "be able to return a failure if input Hts user is not successfully casted to CancelHtsUserReminder object" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))
      val fakeRequest = FakeRequest("POST", "/") //.withBody(Json.toJson(htsReminderUser))
      val invalidFormData = "Not able to cast to CancelHtsUserReminder object"

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.deleteHtsUser()(fakeRequest.withJsonBody(Json.toJson(invalidFormData)))
      status(result) shouldBe 400

    }

    "send back error response if the request do not contain Json body in deleteUser request" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))

      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.deleteHtsUser()(fakeRequest)
      status(result) shouldBe 400

    }

    "be able to return successfully HtsUser if user Nino exists in DB" in {

      val fakeRequest = FakeRequest("GET", "/")
      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))

      inSequence {
        mockGetRepository("AE123456C")(Some(htsReminderUser))
      }

      val result = controller.getHtsUser("AE123456C")(fakeRequest)
      status(result) shouldBe 200

    }

    "return NotFound status if user with Nino does not exist in DB" in {

      val fakeRequest = FakeRequest("GET", "/")

      inSequence {
        mockGetRepository("AE123456C")(None)
      }

      val result = controller.getHtsUser("AE123456C")(fakeRequest)
      status(result) shouldBe 404

    }

    "be able to return a success if Hts users details for email change are correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456D"))

      val updateEmailInput =
        UpdateEmail(htsReminderUser.nino, htsReminderUser.firstName, htsReminderUser.lastName, htsReminderUser.email)

      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-email").withBody(Json.toJson(updateEmailInput))

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

        mockUpdateEmailRepository(
          updateEmailInput.nino.value,
          updateEmailInput.firstName,
          updateEmailInput.lastName,
          updateEmailInput.email)(true)

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 200

    }

    "be able to return a success with Not Found if Hts users details for email change are correct" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456D"))

      val updateEmailInput =
        UpdateEmail(htsReminderUser.nino, htsReminderUser.firstName, htsReminderUser.lastName, htsReminderUser.email)

      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-email").withBody(Json.toJson(updateEmailInput))

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

        mockUpdateEmailRepository(
          updateEmailInput.nino.value,
          updateEmailInput.firstName,
          updateEmailInput.lastName,
          updateEmailInput.email)(false)

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(htsReminderUser)))
      status(result) shouldBe 404

    }

    "return a Bad request response if Hts users details for email change are in-correct" in {

      val inValidFormData = "Not able to Stringify to HtsUser"
      val fakeRequest = FakeRequest("POST", "/")

      implicit val request: Request[JsValue] =
        FakeRequest("POST", "/update-htsuser-email").withBody(Json.toJson(inValidFormData))

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

      }

      val result = controller.updateEmail()(fakeRequest.withJsonBody(Json.toJson(inValidFormData)))
      status(result) shouldBe 400

    }

    "send back error response if the request do not contain Json body in updateEmail request" in {

      val htsReminderUser = (ReminderGenerator.nextReminder).copy(nino = Nino("AE123456C"))

      val fakeRequest = FakeRequest("POST", "/")

      val controller = new HtsUserUpdateController(mockRepository, mcc, auditor, mockAuthConnector)

      inSequence {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
      }

      val result = controller.updateEmail()(fakeRequest)
      status(result) shouldBe 400

    }

  }
}
