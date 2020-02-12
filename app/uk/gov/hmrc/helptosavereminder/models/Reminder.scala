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

package uk.gov.hmrc.helptosavereminder.models

import java.time.LocalDate

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, JsString, Json, Reads, Writes}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class HtsUser(
  nino: Nino,
  email: String,
  name: String = "",
  optInStatus: Boolean = false,
  daysToReceive: Seq[Int] = Seq(),
  nextSendDate: LocalDate = LocalDate.now(),
  bounceCount: Int = 0,
  callBackUrlRef: String = "")

object HtsUser {
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat = ReactiveMongoFormats.objectIdFormats
  implicit val htsUserFormat: Format[HtsUser] = Json.format[HtsUser]
  implicit val writes: Writes[HtsUser] = Writes[HtsUser](s ⇒ JsString(s.toString))

  implicit val reads: Reads[HtsUser] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(Nino.apply(_)) and
      (JsPath \ "email").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "optInStatus").read[Boolean] and
      (JsPath \ "daysToReceive").read[List[Int]] and
      (JsPath \ "nextSendDate").read[LocalDate] and
      (JsPath \ "bounceCount").read[Int] and
      (JsPath \ "callBackUrlRef").read[String]
  )(HtsUser.apply(_, _, _, _, _, _, _, _))

}

object ActorUtils {
  val BOOTSTRAP = "BOOTSTRAP"
  val START = "START"
  val STOP = "STOP"
  val SUCCESS = "SUCCESS"
  val FAILURE = "FAILURE"
}

case class UpdateCallBackRef(reminder: HtsUser, callBackRefUrl: String)

case class UpdateCallBackSuccess(reminder: HtsUser)
