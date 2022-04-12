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

package uk.gov.hmrc.helptosavereminder.connectors

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.Future

class EmailConnectorSpec extends BaseSpec with MongoSpecSupport with MockitoSugar {

  val mockHttp: HttpClient = mock[HttpClient]

  lazy val emailConnector = new EmailConnector(mockHttp)

  "The EmailConnector" should {
    "return true if it can successfully send the email and get 202 response" in {

      val url = "GET /sendEmail"
      val sendTemplatedEmailRequest =
        SendTemplatedEmailRequest(List("emaildid@address.com"), "templateId", Map.empty, "eventUrl")

      when(mockHttp.POST[SendTemplatedEmailRequest, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(202, "")))
      val result = emailConnector.sendEmail(sendTemplatedEmailRequest, url)

      result.futureValue shouldBe true
    }

    "return false if it can successfully send the email and get 202 response" in {

      val url = "GET /sendEmail"
      val sendTemplatedEmailRequest =
        SendTemplatedEmailRequest(List("emaildid@address.com"), "templateId", Map.empty, "eventUrl")

      when(mockHttp.POST[SendTemplatedEmailRequest, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(300, "")))
      val result = emailConnector.sendEmail(sendTemplatedEmailRequest, url)

      result.futureValue shouldBe false
    }

    "successfully unblock an email if submit is successful" in {

      val url = "GET /sendEmail"

      when(mockHttp.DELETE[HttpResponse](any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))
      val result = emailConnector.unBlockEmail(url)

      result.futureValue shouldBe true
    }

    "successfully return with failed future if submit is not-successful" in {

      val url = "GET /sendEmail"

      when(mockHttp.DELETE[HttpResponse](any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(400, "")))
      val result = emailConnector.unBlockEmail(url)

      result.futureValue shouldBe false
    }
  }

}
