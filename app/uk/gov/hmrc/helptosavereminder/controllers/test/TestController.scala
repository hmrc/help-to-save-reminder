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

package uk.gov.hmrc.helptosavereminder.controllers.test

import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.helptosavereminder.config.Scheduler
import uk.gov.hmrc.helptosavereminder.models.ActorUtils.{GET_STATS, START}
import uk.gov.hmrc.helptosavereminder.models.{SendEmails, Stats}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.services.test.TestService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestController @Inject() (
  testService: TestService,
  repository: HtsReminderMongoRepository,
  cc: ControllerComponents,
  scheduler: Scheduler,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext)
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

  def updateEndDate(nino: String, endDate: String): Action[AnyContent] = Action.async {
    repository.findByNino(nino).map {
      case Some(user) =>
        val updatedHtsUser =
          user.copy(endDate = Some(LocalDate.parse(endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))))
        repository.updateReminderUser(updatedHtsUser)
        Ok(Json.toJson(updatedHtsUser))
      case None => NotFound
    }
  }

  private def ifTestActorEnabled(block: => Result): Result =
    if (servicesConfig.getBoolean("testActorEnabled")) {
      block
    } else {
      NotAcceptable("Test actor not enabled")
    }

  def spam(): Action[AnyContent] = Action {
    ifTestActorEnabled {
      scheduler.reminderSupervisor ! START
      Ok("START message sent to the supervisor actor. Emails are being sent now!")
    }
  }

  def spamSpecific(): Action[List[String]] = Action(parse.json[List[String]]) { request =>
    ifTestActorEnabled {
      scheduler.reminderSupervisor ! SendEmails(request.body)
      Ok("START message sent to the supervisor actor. Emails are being sent now!")
    }
  }

  def spamRandom(amount: Int): Action[AnyContent] = Action {
    ifTestActorEnabled {
      val emails = (1 to amount).map(x => s"$x@test.com").toList
      scheduler.reminderSupervisor ! SendEmails(emails)
      Ok("Emails are generated and being sent out.")
    }
  }

  def spamStats(): Action[AnyContent] = Action.async {
    if (servicesConfig.getBoolean("testActorEnabled")) {
      implicit val timeout: Timeout = Timeout(5.seconds)
      for {
        result <- scheduler.reminderSupervisor ? GET_STATS
      } yield Ok(Json.toJson(result.asInstanceOf[Stats]))
    } else {
      Future.successful(NotAcceptable("Test actor not enabled"))
    }
  }

}
