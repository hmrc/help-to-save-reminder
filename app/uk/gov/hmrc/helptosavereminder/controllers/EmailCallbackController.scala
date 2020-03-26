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
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

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
      request.body.asJson.get
        .validate[EventsMap]
        .fold(
          { error =>
            Logger.info(s"Unable to parse Events List for CallBackRequest = $callBackReference")
          }, { (eventsMap: EventsMap) =>
            if (eventsMap.events.exists(x => (x.event == "PermanentBounce"))) {
              val nino = callBackReference.takeRight(9)
              Logger.info("Reminder Callback service called for NINO = " + nino)
              repository.findByNino(nino).flatMap {
                htsUser =>
                  val url = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/${htsUser.get.email}"
                  Logger.info("The URL to request email deletion is " + url)
                  repository.deleteHtsUserByCallBack(nino, callBackReference).map {
                    case Left(error) => {
                      Logger.info("Could not delete from HtsReminder Repository for NINO = " + nino)
                    }
                    case Right(()) => {
                      val path = routes.HtsUserUpdateController.deleteHtsUser().url
                      auditor.sendEvent(
                        HtsReminderUserDeletedEvent(
                          HtsReminderUserDeleted(htsUser.get.nino.nino, Json.toJson(htsUser)),
                          path),
                        htsUser.get.nino.nino)
                      Logger.info(
                        s"[EmailCallbackController] Email deleted from HtsReminder Repository for user = : ${htsUser.get.nino}")
                      http
                        .DELETE(url, Seq(("Content-Type", "application/json")))
                        .onComplete({
                          case Success(response) =>
                            Logger.info(s"Email Service successfully unblocked email for Nino = ${htsUser.get.nino}")
                          case Failure(ex) =>
                            Logger.info(
                              s"Email Service could not unblock email for user Nino = ${htsUser.get.nino} and exception is $ex")
                        })
                    }
                  }
              }
            } else {
              Logger.info(
                s"CallBackRequest received for $callBackReference without PermanentBounce Event and " +
                  s"eventsList received from Email Service = ${eventsMap.events}")
            }
          }
        )
      Future.successful(Ok)
    }
  }
}
