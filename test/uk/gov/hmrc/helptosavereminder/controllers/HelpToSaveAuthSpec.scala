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

import play.api.http.Status
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthorisationException.fromString
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{authProviderId => v2AuthProviderId, nino => v2Nino}
import uk.gov.hmrc.helptosavereminder.auth.HtsReminderAuth
import uk.gov.hmrc.helptosavereminder.auth.HtsReminderAuth._

import scala.concurrent.Future

class HelpToSaveAuthSpec extends AuthSupport {

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

    "handling ggOrPrivilegedAuthorised" must {

      def callAuthNoRetrievals = htsAuth.ggOrPrivilegedAuthorised { _ =>
        Future.successful(Ok("authSuccess"))
      }

      "return after successful auth" in {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))

        val result = callAuthNoRetrievals(FakeRequest())
        result.futureValue.header.status shouldBe Status.OK
      }

    }

    "handling ggOrPrivilegedAuthorisedWithNINO" when {

      def callAuth(nino: Option[String]) = htsAuth.ggOrPrivilegedAuthorisedWithNINO(nino) { _ => _ =>
        Future.successful(Ok("authSuccess"))
      }

      "handling GG requests" when {

        val ggCredentials = GGCredId("")

        "retrieve a NINO and return successfully if the given NINO and retrieved NINO match" in {
          inSequence {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(ggCredentials))
            mockAuth(EmptyPredicate, v2Nino)(Right(Some("nino")))
          }

          val result = callAuth(Some("nino"))(FakeRequest())
          result.futureValue.header.status shouldBe Status.OK
        }

        "retrieve a NINO and return successfully if a NINO is not given and a NINO is successfully retrieved" in {
          inSequence {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(ggCredentials))
            mockAuth(EmptyPredicate, v2Nino)(Right(Some("nino")))
          }

          val result = callAuth(None)(FakeRequest())
          result.futureValue.header.status shouldBe Status.OK
        }

        "retrieve a NINO and return a Forbidden if the given NINO and the retrieved NINO do not match" in {
          inSequence {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(ggCredentials))
            mockAuth(EmptyPredicate, v2Nino)(Right(Some("other-nino")))
          }

          val result = callAuth(Some("nino"))(FakeRequest())
          result.futureValue.header.status shouldBe Status.FORBIDDEN
        }

        "return a Forbidden if a NINO could not be found and a NINO was given" in {
          inSequence {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(ggCredentials))
            mockAuth(EmptyPredicate, v2Nino)(Right(None))
          }

          val result = callAuth(Some("nino"))(FakeRequest())
          result.futureValue.header.status shouldBe Status.FORBIDDEN
        }

        "return a Forbidden if a NINO could not be found and a NINO was not given" in {
          inSequence {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(ggCredentials))
            mockAuth(EmptyPredicate, v2Nino)(Right(None))
          }

          val result = callAuth(None)(FakeRequest())
          result.futureValue.header.status shouldBe Status.FORBIDDEN
        }
      }

      "handling PrivilegedApplication requests" must {

        val privilegedCredentials = PAClientId("")

        "return a BadRequest if no NINO is given" in {
          mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(privilegedCredentials))
          val result = callAuth(None)(FakeRequest())
          result.futureValue.header.status shouldBe Status.BAD_REQUEST
        }

        "return successfully if a NINO is given" in {
          mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(privilegedCredentials))
          val result = callAuth(Some("nino"))(FakeRequest())
          result.futureValue.header.status shouldBe Status.OK
        }

      }

      "handling requests from other AuthProviders" must {

        "return a Forbidden" in {
          for (cred <- List(VerifyPid(""), OneTimeLogin)) {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(cred))
            val result = callAuth(None)(FakeRequest())
            result.futureValue.header.status shouldBe Status.FORBIDDEN
          }

        }

      }

    }

  }
}
