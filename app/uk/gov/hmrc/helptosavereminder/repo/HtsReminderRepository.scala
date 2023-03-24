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

package uk.gov.hmrc.helptosavereminder.repo

import com.google.inject.ImplementedBy
import org.mongodb.scala.model.Filters.{and, equal, lte, regex}
import org.mongodb.scala.model.Indexes.ascending

import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, UpdateOptions, Updates}

import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{Format, JsBoolean, JsError, JsResult, JsString, JsSuccess, JsValue, Json}
import uk.gov.hmrc.helptosavereminder.models.HtsUserSchedule
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions.getNextSendDate
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
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
  def updateEmail(nino: String, firstName: String, lastName: String, email: String): Future[Int]
  def updateEndDate(nino: String, nextSendDate: LocalDate): Future[Boolean]
}

class HtsReminderMongoRepository @Inject() (mongo: MongoComponent)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[HtsUserSchedule](
      mongoComponent = mongo,
      collectionName = "help-to-save-reminder",
      domainFormat = HtsUserSchedule.htsUserFormat,

      indexes = Seq(
        IndexModel(
          ascending("nino"),
          IndexOptions()
            .name("nino")
            .background(true)
        ),
        IndexModel(
          ascending("callBackUrlRef"),
          IndexOptions()
            .name("callBackUrlRef")
            .background(true)
        )
      )

    ) with HtsReminderRepository with Logging {

  override def findHtsUsersToProcess(): Future[Option[List[HtsUserSchedule]]] = {
    logger.debug("findHtsUsersToProcess is about to fetch records")
    val now = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    logger.info(s"time for HtsUsersToProcess $now")
    val testResult = Try {
      collection.find(lte("nextSendDate", now)).sort(equal("nino", 1)).toFuture().map(_.toList)
    }

    testResult match {
      case Success(usersList) => {
        usersList.map(x => {
          logger.info(s"Number of scheduled users fetched = ${x.length}")
          Some(x)
        })
      }
      case Failure(f) => {
        logger.error(s"findHtsUsersToProcess : Exception occurred while fetching users $f ::  ${f.fillInStackTrace()}")
        Future.successful(None)
      }
    }
  }

  override def updateNextSendDate(nino: String, nextSendDate: LocalDate): Future[Boolean] = {
    val result = collection
      .updateOne(
        filter = equal("nino", nino),

        update = Updates.set("nextSendDate", nextSendDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

      )
      .toFuture()

    result
      .map { status =>
        logger.debug(s"[HtsReminderMongoRepository][updateNextSendDate] updated:, result : $status")
        if (status.wasAcknowledged()) true
        else {
          logger.warn(s"Failed to update HtsUser NextSendDate, No Matches Found $status")
          false
        }
      }
      .recover {
        case e =>
          logger.error("Failed to update HtsUser", e)
          false
      }
  }

  override def updateEndDate(nino: String, endDate: LocalDate): Future[Boolean] = {
    val result = collection
      .updateOne(
        filter = equal("nino", nino),

        update = Updates.set("endDate", endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

      )
      .toFuture()

    result
      .map { status =>
        logger.debug(s"[HtsReminderMongoRepository][updateEndDate] updated:, result : $status ")
        if (status.wasAcknowledged()) true
        else {
          logger.warn(s"Failed to update HtsUser EndDatev, No Matches Found: $status")
          false
        }
      }
      .recover {
        case e =>
          logger.error("Failed to update HtsUser", e)
          false
      }
  }

  override def updateEmail(nino: String, firstName: String, lastName: String, email: String): Future[Int] = {
    val result = collection
      .updateOne(
        filter = equal("nino", nino),
        update = Updates
          .combine(Updates.set("email", email), Updates.set("firstName", firstName), Updates.set("lastName", lastName))
      )
      .toFuture()

    result
      .map { updateResult =>
        if (!updateResult.wasAcknowledged()) {
          logger.warn("Failed to update HtsUser Email")
          NOT_FOUND
        } else {
          (updateResult.getMatchedCount, updateResult.getModifiedCount) match {
            case (0, _) =>
              logger.warn("Failed to update HtsUser Email, No Matches Found")
              NOT_FOUND
            case (_, 0) => NOT_MODIFIED
            case (_, _) => OK
          }
        }
      }
      .recover {
        case e =>
          logger.warn("Failed to update HtsUser Email", e)
          NOT_FOUND
      }
  }

  override def updateCallBackRef(nino: String, callBackRef: String): Future[Boolean] = {
    val result = collection
      .updateOne(filter = equal("nino", nino), update = Updates.set("callBackUrlRef", callBackRef))
      .toFuture()

    result
      .map { status =>
        logger.debug(s"[HtsReminderMongoRepository][updateCallBackRef] updated:, result : $status ")
        if (status.wasAcknowledged()) true
        else {
          logger.warn(s"Failed to update HtsUser CallbackRef, No Matches Found $status")
          false
        }
      }
      .recover {
        case e =>
          logger.error("Failed to update HtsUser", e)
          false
      }
  }

  override def updateReminderUser(htsReminder: HtsUserSchedule): Future[Boolean] =
    if (htsReminder.daysToReceive.length <= 0) {
      logger.warn(s"nextSendDate for User: ${htsReminder.nino} cannot be updated.")
      Future.successful(false)
    } else {
      val listOfUpdates = List(
        Updates.set("optInStatus", htsReminder.optInStatus),
        Updates.set("email", htsReminder.email),
        Updates.set("firstName", htsReminder.firstName),
        Updates.set("lastName", htsReminder.lastName),
        Updates.set("daysToReceive", htsReminder.daysToReceive)
      )

      val modifiedJson = if (htsReminder.endDate.nonEmpty) {

        listOfUpdates ::: List(
          Updates.set("endDate", htsReminder.endDate.get.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        )

      } else {
        listOfUpdates
      }

      val updatedModifierJsonCallBackRef = if (htsReminder.callBackUrlRef.isEmpty) {
        modifiedJson ::: List(Updates.set("callBackUrlRef", ""))
      } else {
        modifiedJson ::: List(Updates.set("callBackUrlRef", htsReminder.callBackUrlRef))
      }

      val updatedNextSendDate: Option[LocalDate] =
        getNextSendDate(htsReminder.daysToReceive, LocalDate.now(ZoneId.of("Europe/London")))

      val finalModifiedJson = if (updatedNextSendDate.nonEmpty) {

        updatedModifierJsonCallBackRef ::: List(
          Updates.set("nextSendDate", updatedNextSendDate.get.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        )

      } else {
        logger.warn(s"nextSendDate for User: ${htsReminder.nino} cannot be updated.")
        updatedModifierJsonCallBackRef
      }

      val selector = Filters.equal("nino", htsReminder.nino.value)

      val modifier = Updates.combine(finalModifiedJson.map(update => update): _*)

      val options = UpdateOptions().upsert(true)

      val result = collection.updateOne(selector, modifier, options).toFuture()

      result
        .map { status =>
          logger.debug(s"[HtsReminderMongoRepository][updateReminderUser] updated:, result : $status")
          if (status.wasAcknowledged()) {
            true
          } else {
            logger.warn(s"Failed to update Hts ReminderUser, No Matches Found $status")
            false
          }
        }
        .recover {
          case e =>
            logger.warn("Failed to update HtsUser", e)
            false
        }
    }

  override def deleteHtsUser(nino: String): Future[Either[String, Unit]] = {
    logger.debug(nino)
    collection
      .deleteOne(regex("nino", nino))
      .toFuture()
      .map[Either[String, Unit]] { res =>
        if (res.getDeletedCount > 0) Right(())
        else {
          Left(s"Could not find htsUser to delete")
        }
      }
      .recover {
        case e ⇒
          Left(s"Could not delete htsUser: ${e.getMessage}")
      }
  }

  override def deleteHtsUserByCallBack(nino: String, callBackUrlRef: String): Future[Either[String, Unit]] =
    collection
      .deleteOne(and(regex("nino", nino), equal("callBackUrlRef", callBackUrlRef)))
      .toFuture()
      .map[Either[String, Unit]] { res =>
        if (res.getDeletedCount > 0) Right(())
        else {
          Left(s"Could not find htsUser to delete by callBackUrlRef")
        }
      }
      .recover {
        case e ⇒
          Left(s"Could not delete htsUser by callBackUrlRef : ${e.getMessage}")
      }
  override def findByNino(nino: String): Future[Option[HtsUserSchedule]] =
    collection.find(Filters.eq("nino", nino)).toFuture().map(_.headOption)

  override def findByCallBackUrlRef(callBackUrlRef: String): Future[Option[HtsUserSchedule]] =
    collection.find(Filters.eq("callBackUrlRef", callBackUrlRef)).toFuture().map(_.headOption)

}
