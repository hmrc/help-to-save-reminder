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
import play.api.http.Status.{ACCEPTED, BAD_REQUEST, MULTIPLE_CHOICES, OK}
import uk.gov.hmrc.helptosavereminder.base.BaseSpec
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest
import uk.gov.hmrc.helptosavereminder.utils.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.test.MongoSupport

class EmailConnectorSpec extends BaseSpec with MongoSupport with IdiomaticMockito {

  val headers: Map[String, String] = Map("Content-Type" -> "application/json")
  lazy val emailConnector: EmailConnector = app.injector.instanceOf[EmailConnector]

  "The EmailConnector" should {

    "return true if it can successfully send the email and get 202 response" in {
      val sendTemplatedEmailRequest =
        SendTemplatedEmailRequest(List("emaildid@address.com"), "templateId", Map.empty, force = false, "eventUrl")

      when(POST, "/hmrc/email", headers = headers).thenReturn(ACCEPTED)

      val result = emailConnector.sendEmail(sendTemplatedEmailRequest)
      result.futureValue shouldBe true
    }

    "return false if it can successfully send the email and get 202 response" in {
      val sendTemplatedEmailRequest =
        SendTemplatedEmailRequest(List("emaildid@address.com"), "templateId", Map.empty, force = false, "eventUrl")

      when(POST, "/hmrc/email", headers = headers).thenReturn(MULTIPLE_CHOICES)

      val result = emailConnector.sendEmail(sendTemplatedEmailRequest)
      result.futureValue shouldBe false
    }

    "successfully unblock an email if submit is successful" in {
      val email = "email@test.com"

      when(DELETE, s"/hmrc/bounces/$email", headers = headers).thenReturn(OK)

      val result = emailConnector.unBlockEmail(email)
      result.futureValue shouldBe true
    }

    "successfully return with failed future if submit is not-successful" in {
      val email = "email@test.com"

      when(DELETE, s"/hmrc/bounces/$email", headers = headers).thenReturn(BAD_REQUEST)

      val result = emailConnector.unBlockEmail(email)
      result.futureValue shouldBe false
    }
  }
}
