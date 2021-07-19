/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavereminder.models

import play.api.libs.json.{Json, OFormat}

case class HtsReminderTemplate(email: String, name: String, callBackUrlRef: String, monthName: String)

case class SendTemplatedEmailRequest(
  to: List[String],
  templateId: String,
  parameters: Map[String, String],
  eventUrl: String
)

object SendTemplatedEmailRequest {
  implicit val format: OFormat[SendTemplatedEmailRequest] = Json.format[SendTemplatedEmailRequest]
}

object HtsReminderTemplate {
  def apply(email: String, name: String, callBackUrlRef: String, monthName: String): HtsReminderTemplate = {
    def format(fullName: String) =
      try {
        fullName
          .split(" ")
          .map(name => name(0).toUpper + name.substring(1).toLowerCase)
          .mkString(" ")
          .split("-")
          .map(name => name(0).toUpper + name.substring(1))
          .mkString("-")
      } catch {
        case _: Throwable => fullName
      }
    new HtsReminderTemplate(email, format(name), callBackUrlRef, monthName)
  }
}
