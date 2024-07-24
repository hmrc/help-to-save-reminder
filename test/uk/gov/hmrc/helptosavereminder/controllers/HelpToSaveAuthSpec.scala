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

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthorisationException.fromString
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino => v2Nino}
import uk.gov.hmrc.helptosavereminder.auth.HtsReminderAuth
import uk.gov.hmrc.helptosavereminder.auth.HtsReminderAuth._

import scala.concurrent.Future

class HelpToSaveAuthSpec extends AuthSupport {

  // mockAuth takes a while
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  val htsAuth = new HtsReminderAuth(mockAuthConnector, testCC)

  "HelpToSaveAuth" when {
    "handling ggAuthorisedWithNINO" must {
      def callAuth = htsAuth.ggAuthorisedWithNino { _ => _ =>
        Future.successful(Ok(s"authSuccess"))
      }

      "return after successful authentication" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))

        val result = callAuth(FakeRequest())
        result.futureValue.header.status shouldBe Status.OK
      }

      "return a forbidden if nino is not found" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(None))

        val result = callAuth(FakeRequest())
        result.futureValue.header.status shouldBe Status.FORBIDDEN
      }

      "handle various auth related exceptions and throw an error" in {
        def mockAuthWith(error: String): Unit = mockAuth(AuthWithCL200, v2Nino)(Left(fromString(error)))

        val exceptions = List(
          "InsufficientConfidenceLevel" -> Status.FORBIDDEN,
          "InsufficientEnrolments"      -> Status.FORBIDDEN,
          "UnsupportedAffinityGroup"    -> Status.FORBIDDEN,
          "UnsupportedCredentialRole"   -> Status.FORBIDDEN,
          "UnsupportedAuthProvider"     -> Status.FORBIDDEN,
          "BearerTokenExpired"          -> Status.UNAUTHORIZED,
          "MissingBearerToken"          -> Status.UNAUTHORIZED,
          "InvalidBearerToken"          -> Status.UNAUTHORIZED,
          "SessionRecordNotFound"       -> Status.UNAUTHORIZED,
          "IncorrectCredentialStrength" -> Status.FORBIDDEN,
          "unknown-blah"                -> Status.INTERNAL_SERVER_ERROR
        )

        for ((error, expectedStatus) <- exceptions) {
          mockAuthWith(error)
          val result = callAuth(FakeRequest())
          result.futureValue.header.status shouldBe expectedStatus
        }
      }
    }
  }
}
