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

package uk.gov.hmrc.helptosavereminder.controllers

import com.google.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.models.{EventsMap, HtsReminderUserDeleted, HtsReminderUserDeletedEvent}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EmailCallbackController @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig,
  val cc: MessagesControllerComponents,
  repository: HtsReminderMongoRepository,
  auditor: HTSAuditor)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends BackendController(cc) {

  def handleCallBack(callBackReference: String): Action[AnyContent] = Action.async { implicit request =>
    {
      request.body.asJson
        .getOrElse(Json.toJson(""))
        .validate[EventsMap]
        .fold(
          { error =>
            Logger.error(s"Unable to parse Events List for CallBackRequest = $callBackReference")
            Future.failed(new Exception("Error parsing the hts schedule"))
          }, { (eventsMap: EventsMap) =>
            if (eventsMap.events.exists(x => (x.event.contains("PermanentBounce")))) {
              val nino = callBackReference.takeRight(9)
              Logger.info("Reminder Callback service called for NINO = " + nino)
              repository.findByNino(nino).flatMap {
                case Some(htsUser) =>
                  val url = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/${htsUser.email}"
                  Logger.debug("The URL to request email deletion is " + url)
                  repository.deleteHtsUserByCallBack(nino, callBackReference).flatMap {
                    case Left(error) => {
                      Logger.error("Could not delete from HtsReminder Repository for NINO = " + nino)
                      Future.successful(Ok("Error deleting the hts schedule by nino"))
                    }
                    case Right(()) => {
                      val path = routes.HtsUserUpdateController.deleteHtsUser().url
                      auditor.sendEvent(
                        HtsReminderUserDeletedEvent(
                          HtsReminderUserDeleted(htsUser.nino.nino, Json.toJson(htsUser)),
                          path),
                        htsUser.nino.nino)
                      Logger.debug(
                        s"[EmailCallbackController] Email deleted from HtsReminder Repository for user = : ${htsUser.nino}")
                      http
                        .DELETE(url, Seq(("Content-Type", "application/json")))
                        .onComplete({
                          case Success(response) =>
                            Logger.debug(s"Email Service successfully unblocked email for Nino = ${htsUser.nino}")
                          case Failure(ex) =>
                            Logger.error(
                              s"Email Service could not unblock email for user Nino = ${htsUser.nino} and exception is $ex")
                        })
                      Future.successful(Ok)
                    }
                  }
                case None => Future.failed(new Exception("No Hts Schedule found"))
              }
            } else {
              Logger.debug(
                s"CallBackRequest received for $callBackReference without PermanentBounce Event and " +
                  s"eventsList received from Email Service = ${eventsMap.events}")
              Future.successful(Ok)
            }
          }
        )
    }
  }
}
