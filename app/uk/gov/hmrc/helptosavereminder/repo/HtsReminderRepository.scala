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

package uk.gov.hmrc.helptosavereminder.repo

import java.time.{LocalDate, ZoneId}

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.collections.GenericCollection
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.helptosavereminder.models.HtsUserSchedule
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions.getNextSendDate

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[HtsReminderMongoRepository])
trait HtsReminderRepository {
  def findHtsUsersToProcess(): Future[Option[List[HtsUserSchedule]]]
  def updateNextSendDate(nino: String, nextSendDate: LocalDate): Future[Boolean]
  def updateCallBackRef(nino: String, callBackRef: String): Future[Boolean]
  def updateReminderUser(htsReminder: HtsUserSchedule): Future[Boolean]
  def findByNino(nino: String): Future[Option[HtsUserSchedule]]
  def findByCallBackUrlRef(callBackUrlRef: String): Future[Option[HtsUserSchedule]]
  def deleteHtsUser(nino: String): Future[Either[String, Unit]]
  def deleteHtsUserByCallBack(nino: String, callBackUrlRef: String): Future[Either[String, Unit]]
  def updateEmail(nino: String, firstName: String, lastName: String, email: String): Future[Boolean]
}

class HtsReminderMongoRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[HtsUserSchedule, BSONObjectID](
      collectionName = "help-to-save-reminder",
      mongo = mongo.mongoConnector.db,
      HtsUserSchedule.htsUserFormat,
      ReactiveMongoFormats.objectIdFormats
    ) with HtsReminderRepository {

  lazy val proxyCollection: GenericCollection[JSONSerializationPack.type] = collection

  override def findHtsUsersToProcess(): Future[Option[List[HtsUserSchedule]]] = {
    Logger.debug("findHtsUsersToProcess is about to fetch records")
    val testResult = Try {
      proxyCollection
        .find(Json.obj(), Option.empty[JsObject])
        .sort(Json.obj("nino" -> 1))
        .cursor[HtsUserSchedule](ReadPreference.primary)
        .collect[List](-1, Cursor.FailOnError[List[HtsUserSchedule]]())
    }

    testResult match {
      case Success(usersList) => {
        usersList.map(x => {
          Logger.debug(s"No of user schedules fetched = ${x.length}")
          Some(x)
        })
      }
      case Failure(f) => {
        Logger.error(s"findHtsUsersToProcess : Exception occurred while fetching users $f ::  ${f.fillInStackTrace()}")
        Future.successful(None)
      }
    }
  }

  override def updateNextSendDate(nino: String, nextSendDate: LocalDate): Future[Boolean] = {
    val selector = Json.obj("nino" -> nino)
    val modifier = Json.obj("$set" -> Json.obj("nextSendDate" -> nextSendDate))
    val result = proxyCollection.update(ordered = false).one(selector, modifier)

    result
      .map { status =>
        Logger.debug(s"[HtsReminderMongoRepository][updateNextSendDate] updated:, result : $status ")
        statusCheck("Failed to update HtsUser NextSendDate, No Matches Found", status)
      }
      .recover {
        case e =>
          Logger.error("Failed to update HtsUser", e)
          false
      }

  }

  override def updateEmail(nino: String, firstName: String, lastName: String, email: String): Future[Boolean] = {
    val selector = Json.obj("nino" -> nino)
    val modifier = Json.obj("$set" -> Json.obj("email" -> email, "firstName" -> firstName, "lastName" -> lastName))
    val result = proxyCollection.update(ordered = false).one(selector, modifier)

    result
      .map { status =>
        Logger.debug(s"[HtsReminderMongoRepository][updateEmail] updated:, result : $status ")
        statusCheck("Failed to update HtsUser Email, No Matches Found", status)
      }
      .recover {
        case e =>
          Logger.error("Failed to update HtsUser Email", e)
          false
      }

  }

  override def updateCallBackRef(nino: String, callBackRef: String): Future[Boolean] = {
    val selector = Json.obj("nino" -> nino)
    val modifier = Json.obj("$set" -> Json.obj("callBackUrlRef" -> callBackRef))

    val result = proxyCollection.update(ordered = false).one(selector, modifier)

    result
      .map { status =>
        Logger.debug(s"[HtsReminderMongoRepository][updateCallBackRef] updated:, result : $status ")
        statusCheck("Failed to update HtsUser CallbackRef, No Matches Found", status)
      }
      .recover {
        case e =>
          Logger.error("Failed to update HtsUser", e)
          false
      }

  }

  override def updateReminderUser(htsReminder: HtsUserSchedule): Future[Boolean] = {

    val selector = Json.obj("nino" -> htsReminder.nino.value)

    if (htsReminder.daysToReceive.length <= 0) {
      Logger.error(s"nextSendDate for User: $htsReminder cannot be updated.")
      Future.successful(false)
    } else {
      val modifierJson = Json.obj(
        "optInStatus"   -> JsBoolean(htsReminder.optInStatus),
        "email"         -> htsReminder.email,
        "firstName"     -> htsReminder.firstName,
        "lastName"      -> htsReminder.lastName,
        "daysToReceive" -> htsReminder.daysToReceive
      )

      val updatedModifierJsonCallBackRef =
        if (htsReminder.callBackUrlRef.isEmpty)
          modifierJson ++ Json.obj("callBackUrlRef"    -> "")
        else modifierJson ++ Json.obj("callBackUrlRef" -> htsReminder.callBackUrlRef)

      val updatedNextSendDate: Option[LocalDate] =
        getNextSendDate(htsReminder.daysToReceive, LocalDate.now(ZoneId.of("Europe/London")))

      val finalModifiedJson = updatedNextSendDate match {
        case Some(localDate) => updatedModifierJsonCallBackRef ++ Json.obj("nextSendDate" -> localDate)
        case None =>
          Logger.error(s"nextSendDate for User: $htsReminder cannot be updated.")
          updatedModifierJsonCallBackRef
      }

      val modifier = Json.obj(
        "$set" -> finalModifiedJson
      )

      val result = proxyCollection.update(ordered = false).one(selector, modifier, upsert = true)

      result
        .map { status =>
          Logger.debug(s"[HtsReminderMongoRepository][updateReminderUser] updated:, result : $status")
          statusCheck("Failed to update Hts ReminderUser, No Matches Found", status)
        }
        .recover {
          case e =>
            Logger.error("Failed to update HtsUser", e)
            false
        }
    }

  }

  def statusCheck(errorMsg: String, status: UpdateWriteResult): Boolean =
    status.n match {
      case 0 => {
        Logger.error(errorMsg)
        false
      }
      case _ => status.ok
    }

  override def deleteHtsUser(nino: String): Future[Either[String, Unit]] =
    remove("nino" → Json.obj("$regex" → JsString(nino)))
      .map[Either[String, Unit]] { res ⇒
        if (res.writeErrors.nonEmpty) {
          Left(s"Could not delete htsUser: ${res.writeErrors.mkString(";")}")
        } else {
          Right(())
        }
      }
      .recover {
        case e ⇒
          Left(s"Could not delete htsUser: ${e.getMessage}")
      }

  override def deleteHtsUserByCallBack(nino: String, callBackUrlRef: String): Future[Either[String, Unit]] =
    remove("nino" → Json.obj("$regex" → JsString(nino), "callBackUrlRef" -> callBackUrlRef))
      .map[Either[String, Unit]] { res ⇒
        if (res.writeErrors.nonEmpty) {
          Left(s"Could not delete htsUser by callBackUrlRef: ${res.writeErrors.mkString(";")}")
        } else {
          Right(())
        }
      }
      .recover {
        case e ⇒
          Left(s"Could not delete htsUser by callBackUrlRef : ${e.getMessage}")
      }

  override def findByNino(nino: String): Future[Option[HtsUserSchedule]] = {

    val tryResult = Try {
      proxyCollection
        .find(Json.obj("nino" -> nino), Option.empty[JsObject])
        .cursor[HtsUserSchedule](ReadPreference.primary)
        .collect[List](maxDocs = 1, err = Cursor.FailOnError[List[HtsUserSchedule]]())
    }

    tryResult match {
      case Success(s) => {
        s.map { x =>
          x.headOption
        }
      }
      case Failure(f) => {
        Future.successful(None)
      }
    }
  }

  override def findByCallBackUrlRef(callBackUrlRef: String): Future[Option[HtsUserSchedule]] = {

    val tryResult = Try {
      proxyCollection
        .find(Json.obj("callBackUrlRef" -> callBackUrlRef), Option.empty[JsObject])
        .cursor[HtsUserSchedule](ReadPreference.primary)
        .collect[List](maxDocs = 1, err = Cursor.FailOnError[List[HtsUserSchedule]]())
    }

    tryResult match {
      case Success(s) => {
        s.map { x =>
          x.headOption
        }
      }
      case Failure(f) => {
        Future.successful(None)
      }
    }
  }

  override def indexes: Seq[Index] = Seq(
    Index(Seq("nino"           -> IndexType.Ascending), Some("nino"), background = true),
    Index(Seq("callBackUrlRef" -> IndexType.Ascending), Some("callBackUrlRef"), background = true)
  )

}
