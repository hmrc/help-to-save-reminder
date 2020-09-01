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
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.models.{EventsMap, HtsReminderUserDeleted, HtsReminderUserDeletedEvent}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.helptosavereminder.util.JsErrorOps._

import cats.instances.string._
import cats.syntax.eq._

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
    request.body.asJson.map(_.validate[EventsMap]) match {
      case Some(JsSuccess(eventsMap, _)) ⇒ {
        if (eventsMap.events.exists(x => (x.event === "PermanentBounce"))) {
          val nino = callBackReference.takeRight(9)
          Logger.info(s"Reminder Callback service called for NINO = $nino")
          repository.findByNino(nino).flatMap {
            case Some(htsUser) =>
              val url = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/${htsUser.email}"
              Logger.debug(s"The URL to request email deletion is $url")
              repository.deleteHtsUserByCallBack(nino, callBackReference).flatMap {
                case Left(error) => {
                  Logger.error(s"Could not delete from HtsReminder Repository for NINO = $nino")
                  Future.successful(Ok("Error deleting the hts schedule by nino"))
                }
                case Right(()) => {
                  val path = routes.HtsUserUpdateController.deleteHtsUser().url
                  auditor.sendEvent(
                    HtsReminderUserDeletedEvent(
                      HtsReminderUserDeleted(htsUser.nino.toString, Json.toJson(htsUser)),
                      path),
                    htsUser.nino.toString)
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
      case Some(error: JsError) ⇒
        val errorString = error.prettyPrint()
        Logger.error(s"Unable to parse Events List for CallBackRequest = $errorString")
        Future.successful(BadRequest(s"Unable to parse Events List for CallBackRequest = $errorString"))

      case None ⇒
        Logger.warn("No JSON body found in request")
        Future.successful(BadRequest(s"No JSON body found in request"))
    }
  }
}
