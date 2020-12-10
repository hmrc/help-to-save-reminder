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

package uk.gov.hmrc.helptosavereminder.controllers.test

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.helptosavereminder.services.test.TestService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class TestController @Inject()(
  testService: TestService,
  repository: HtsReminderMongoRepository,
  cc: ControllerComponents)
    extends BackendController(cc) {

  def populateReminders(noUsers: Int, emailPrefix: String, daysToReceive: Seq[Int]): Action[AnyContent] = Action.async {
    Future
      .sequence((0 until noUsers).map(_ => testService.generateAndInsertReminder(emailPrefix, daysToReceive)))
      .map(_ => Ok)
  }

  def getHtsUser(nino: String): Action[AnyContent] = Action.async {
    repository.findByNino(nino).map {
      case Some(htsUser) => Ok(Json.toJson(htsUser))
      case None          => NotFound
    }
  }
}
