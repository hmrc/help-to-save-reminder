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

package uk.gov.hmrc.helptosavereminder.models

import org.mongodb.scala.bson.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

case class HtsUserSchedule(
  nino: Nino,
  email: String,
  firstName: String = "",
  lastName: String = "",
  optInStatus: Boolean = false,
  daysToReceive: Seq[Int] = Seq(),
  nextSendDate: LocalDate = LocalDate.now(),
  callBackUrlRef: String = "",
  endDate: Option[LocalDate] = None
)

case class UpdateCallBackRef(reminder: HtsUserScheduleMsg, callBackRefUrl: String)

case class UpdateCallBackSuccess(reminder: HtsUserScheduleMsg, callBackRefUrl: String)

case class CancelHtsUserReminder(nino: String)

case class UpdateEmail(nino: Nino, firstName: String, lastName: String, email: String)

object HtsUserSchedule {
  implicit val dateFormat: Format[LocalDate] = new Format[LocalDate] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override def writes(ldate: LocalDate) = Json.toJson(ldate.format(formatter))

    override def reads(json: JsValue): JsResult[LocalDate] = json match {
      case JsString(s) =>
        Try(LocalDate.parse(s, formatter)) match {
          case Success(date)  => JsSuccess(date)
          case Failure(error) => JsError(s"Could not parse date as yyyyMMdd: ${error.getMessage}")
        }
      case _ => JsError(s"Could not parse date")
    }
  }
  implicit val idFormat: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val htsUserFormat: Format[HtsUserSchedule] = Json.format[HtsUserSchedule]

  implicit val reads: Reads[HtsUserSchedule] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(Nino.apply(_)) and
      (JsPath \ "email").read[String] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      (JsPath \ "optInStatus").read[Boolean] and
      (JsPath \ "daysToReceive").read[List[Int]] and
      (JsPath \ "nextSendDate").read[LocalDate] and
      (JsPath \ "callBackUrlRef").read[String] and
      (JsPath \ "endDate").readNullable[LocalDate]
  )(HtsUserSchedule.apply(_, _, _, _, _, _, _, _, _))

}

object ActorUtils {
  val BOOTSTRAP = "BOOTSTRAP"
  val START = "START"
  val STOP = "STOP"
  val SUCCESS = "SUCCESS"
  val FAILURE = "FAILURE"
}

object CancelHtsUserReminder {

  implicit val htsUserCancelFormat: Format[CancelHtsUserReminder] = Json.format[CancelHtsUserReminder]

  implicit val writes: Writes[CancelHtsUserReminder] = Writes[CancelHtsUserReminder](s => JsString(s.toString))

  implicit val reads: Reads[CancelHtsUserReminder] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(CancelHtsUserReminder.apply(_))
  )
}

object UpdateEmail {

  implicit val htsUpdateEmailFormat: Format[UpdateEmail] = Json.format[UpdateEmail]

  implicit val writes: Writes[UpdateEmail] = Writes[UpdateEmail](s => JsString(s.toString))

  implicit val reads: Reads[UpdateEmail] = (
    (JsPath \ "nino").read[String].orElse((JsPath \ "nino").read[String]).map(Nino.apply(_)) and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      (JsPath \ "email").read[String]
  )(UpdateEmail.apply(_, _, _, _))

}

case class EventsMap(events: List[EventItem])

object EventsMap {

  implicit val eventsMapFormat: Format[EventsMap] = Json.format[EventsMap]

  implicit val writes: Writes[EventsMap] = Writes[EventsMap](s => JsString(s.toString))

  implicit val reads: Reads[EventsMap] = Json.reads[EventsMap]

}

case class EventItem(event: String, detected: LocalDateTime)

object EventItem {

  implicit val eventFormat: Format[EventItem] = Json.format[EventItem]

  implicit val writes: Writes[EventItem] = Writes[EventItem](s => JsString(s.toString))

  implicit val reads: Reads[EventItem] = (
    (JsPath \ "event").read[String] and
      (JsPath \ "detected").read[LocalDateTime]
  )(EventItem.apply(_, _))

}

case class HtsUserScheduleMsg(htsUserSchedule: HtsUserSchedule, currentDate: LocalDate)
