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

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.collections.GenericCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.helptosavereminder.models.Schedule
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.play.json.JSONSerializationPack

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[SchedulerMongoRepository])
trait SchedulerRepository {
  def createSchedule(schedule: Schedule): Future[Either[String, Schedule]]
}

class SchedulerMongoRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Schedule, BSONObjectID](
      collectionName = "schedules",
      mongo = mongo.mongoConnector.db,
      Schedule.scheduleFormat,
      ReactiveMongoFormats.objectIdFormats
    ) with SchedulerRepository {

  lazy val proxyCollection: GenericCollection[JSONSerializationPack.type] = collection

  override def createSchedule(schedule: Schedule): Future[Either[String, Schedule]] =
    insert(schedule)
      .map(result =>
        if (result.ok) {
          Right(schedule)
        } else {
          Left("Unexpected error while creating Schedule ")
      })

  override def indexes: Seq[Index] = Seq(
    Index(Seq("nextExecutionAt" -> IndexType.Ascending), Some("nextExecutionAt"), background = true)
  )

}
