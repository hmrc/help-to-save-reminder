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

import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import org.mockito.stubbing.ScalaOngoingStubbing
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.helptosavereminder.auth.HtsReminderAuth._
import uk.gov.hmrc.helptosavereminder.base.BaseSpec

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait AuthSupport extends BaseSpec with IdiomaticMockito {

  val nino = "AE123456C"

  val mockedNinoRetrieval: Option[NINO] = Some(nino)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuth[A](predicate: Predicate, retrieval: Retrieval[A])(
    result: Either[Exception, A]
  ): ScalaOngoingStubbing[Future[A]] =
    mockAuthConnector
      .authorise(predicate, retrieval)(*, *)
      .returns(result match {
        case Left(e)  => Future.failed[A](e)
        case Right(r) => Future.successful(r)
      })

  def mockAuth[A](
    retrieval: Retrieval[A]
  )(result: Either[Exception, A]): Any =
    mockAuthConnector
      .authorise(*, retrieval)(*, *)
      .returns(result match {
        case Left(e)  => Future.failed[A](e)
        case Right(r) => Future.successful(r)
      })

  def testWithGGAndPrivilegedAccess(f: (() ⇒ Unit) ⇒ Unit): Unit = {
    withClue("For GG access: ") {
      f { () =>
        mockAuth(GGAndPrivilegedProviders, v2.Retrievals.authProviderId)(Right(GGCredId("id")))
        mockAuth(EmptyPredicate, v2.Retrievals.nino)(Right(Some(nino)))
      }
    }

    withClue("For privileged access: ") {
      f { () ⇒
        mockAuth(GGAndPrivilegedProviders, v2.Retrievals.authProviderId)(Right(PAClientId("id")))
      }
    }
  }

  "Calls to maskNino on util package" should {
    "return appropriate strings" in {
      maskNino("SK614711A") shouldBe "<NINO>"
      maskNino("") shouldBe ""

      toFuture("FutureString").onComplete({
        case Success(value)     => value shouldBe "FutureString"
        case Failure(exception) => new Exception(s"Call to maskNino failed because of $exception")
      })
    }
  }
}
