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
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector

import scala.concurrent.{ExecutionContext, Future}

class EmailCallbackController @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig,
  val cc: MessagesControllerComponents,
  repository: HtsReminderMongoRepository,
  auditor: HTSAuditor,
  emailConnector: EmailConnector)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends BackendController(cc) {

  def handleCallBack(callBackReference: String): Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.map(_.validate[EventsMap]) match {
      case Some(JsSuccess(eventsMap, _)) ⇒ {
        if (eventsMap.events.exists(x => (x.event === "PermanentBounce"))) {
          Logger.info(s"Reminder Callback service called for callBackReference = $callBackReference")
          repository.findByCallBackUrlRef(callBackReference).flatMap {
            case Some(htsUserSchedule) =>
              val url = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/${htsUserSchedule.email}"
              Logger.debug(s"The URL to request email deletion is $url")
              repository.deleteHtsUserByCallBack(htsUserSchedule.nino.value, callBackReference).flatMap {
                case Left(error) => {
                  Logger.error(s"Could not delete from HtsReminder Repository for NINO = ${htsUserSchedule.nino.value}")
                  Future.successful(Ok(s"Error deleting the hts schedule by callBackReference = $callBackReference"))
                }
                case Right(()) => {
                  val path = routes.EmailCallbackController.handleCallBack(callBackReference).url
                  auditor.sendEvent(
                    HtsReminderUserDeletedEvent(
                      HtsReminderUserDeleted(htsUserSchedule.nino.toString, Json.toJson(htsUserSchedule)),
                      path),
                    htsUserSchedule.nino.toString)
                  Logger.debug(
                    s"[EmailCallbackController] Email deleted from HtsReminder Repository for user = : ${htsUserSchedule.nino}")

                  emailConnector
                    .unBlockEmail(url) map { response =>
                    response match {
                      case true =>
                        Logger.debug(s"Email successfully unblocked for request : $url")
                        Future.successful(Ok)
                      case _ =>
                        Logger.debug(s"A request to unblock for Email is returned with error for $url")
                        Future.successful(NOT_FOUND)
                    }
                  }
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
