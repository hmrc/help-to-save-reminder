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

package uk.gov.hmrc.helptosavereminder.connectors

import org.mockito.IdiomaticMockito
import org.mockito.ArgumentMatchersSugar.*
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

class EmailConnectorSpec extends BaseSpec with MongoSupport with IdiomaticMockito {

  val mockHttp: HttpClient = mock[HttpClient]

  lazy val emailConnector = new EmailConnector(mockHttp, app.injector.instanceOf[ServicesConfig])

  "The EmailConnector" should {
    "return true if it can successfully send the email and get 202 response" in {
      val sendTemplatedEmailRequest =
        SendTemplatedEmailRequest(List("emaildid@address.com"), "templateId", Map.empty, force = false, "eventUrl")

      mockHttp
        .POST[SendTemplatedEmailRequest, HttpResponse]("http://localhost:7002/hmrc/email", *, *)(*, *, *, *)
        .returns(Future.successful(HttpResponse(202, "")))
      val result = emailConnector.sendEmail(sendTemplatedEmailRequest)

      result.futureValue shouldBe true
    }

    "return false if it can successfully send the email and get 202 response" in {
      val sendTemplatedEmailRequest =
        SendTemplatedEmailRequest(List("emaildid@address.com"), "templateId", Map.empty, force = false, "eventUrl")

      mockHttp
        .POST[SendTemplatedEmailRequest, HttpResponse]("http://localhost:7002/hmrc/email", *, *)(*, *, *, *)
        .returns(Future.successful(HttpResponse(300, "")))
      val result = emailConnector.sendEmail(sendTemplatedEmailRequest)

      result.futureValue shouldBe false
    }

    "successfully unblock an email if submit is successful" in {
      val email = "email@test.com"

      mockHttp
        .DELETE[HttpResponse](s"http://localhost:7002/hmrc/bounces/$email", *)(*, *, *)
        .returns(Future.successful(HttpResponse(200, "")))
      val result = emailConnector.unBlockEmail(email)

      result.futureValue shouldBe true
    }

    "successfully return with failed future if submit is not-successful" in {
      val email = "email@test.com"

      mockHttp
        .DELETE[HttpResponse](s"http://localhost:7002/hmrc/bounces/$email", *)(*, *, *)
        .returns(Future.successful(HttpResponse(400, "")))
      val result = emailConnector.unBlockEmail(email)

      result.futureValue shouldBe false
    }
  }

}
