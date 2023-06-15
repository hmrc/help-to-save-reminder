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

package uk.gov.hmrc.helptosavereminder.controllers

import cats.data.EitherT
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.helptosavereminder.audit.HTSAuditor
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.{EventsMap, HtsReminderUserDeleted, HtsReminderUserDeletedEvent, HtsUserSchedule}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.helptosavereminder.util.JsErrorOps._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class EmailCallbackController @Inject() (
  servicesConfig: ServicesConfig,
  val cc: MessagesControllerComponents,
  repository: HtsReminderMongoRepository,
  auditor: HTSAuditor,
  emailConnector: EmailConnector
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends BackendController(cc) with Logging {

  def handleCallBack(callBackReference: String): Action[AnyContent] = Action.async { implicit request =>
    (for {
      eventsMap <- request.body.asJson.map(_.validate[EventsMap]) match {
                    case Some(error: JsError) =>
                      val errorString = error.prettyPrint()
                      logger.debug(s"Unable to parse Events List for CallBackRequest = $errorString")
                      logger.warn(s"Unable to parse Events List for callBackReference = $callBackReference")
                      EitherT[Future, Result, EventsMap](
                        Future.successful(
                          Left(BadRequest(s"Unable to parse Events List for CallBackRequest = $errorString"))
                        )
                      )

                    case None =>
                      logger.warn(s"No JSON body found in request for callBackReference = $callBackReference")
                      EitherT[Future, Result, EventsMap](
                        Future.successful(Left(BadRequest(s"No JSON body found in request")))
                      )

                    case Some(JsSuccess(eventsMap, _)) =>
                      EitherT[Future, Result, EventsMap](Future.successful(Right(eventsMap)))
                  }
      _ <- if (eventsMap.events.exists(x => (x.event === "PermanentBounce"))) {
            logger.info(s"Reminder Callback service called for callBackReference = $callBackReference")
            EitherT.fromEither[Future](Right())
          } else {
            logger.debug(
              s"CallBackRequest received for $callBackReference without PermanentBounce Event and " +
                s"eventsList received from Email Service = ${eventsMap.events}"
            )
            EitherT.fromEither[Future](Left(Ok))
          }
      htsUserSchedule <- EitherT[Future, Result, HtsUserSchedule](
                          repository.findByCallBackUrlRef(callBackReference).map {
                            case None                  => throw new Exception("No Hts Schedule found")
                            case Some(htsUserSchedule) => Right(htsUserSchedule)
                          }
                        )
      val url = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/${htsUserSchedule.email}"
      _ = logger.debug(s"The URL to request email deletion is $url")
      _ <- EitherT(repository.deleteHtsUserByCallBack(htsUserSchedule.nino.value, callBackReference)).leftMap(error => {
            logger.warn(
              s"Could not delete from HtsReminder Repository for NINO = ${htsUserSchedule.nino.value}, $error"
            )
            Ok(s"Error deleting the hts schedule by callBackReference = $callBackReference")
          })
      _ = {
        val path = routes.EmailCallbackController.handleCallBack(callBackReference).url
        auditor.sendEvent(
          HtsReminderUserDeletedEvent(
            HtsReminderUserDeleted(htsUserSchedule.nino.value, htsUserSchedule.email),
            path
          )
        )
        logger.debug(
          s"[EmailCallbackController] Email deleted from HtsReminder Repository for user = : ${htsUserSchedule.nino}"
        )
      }
      _ = for {
        wasUnblocked <- EitherT.liftF(emailConnector.unBlockEmail(url))
      } yield
        if (wasUnblocked) {
          logger.debug(s"Email successfully unblocked for request : $url")
          logger.info(s"Email successfully unblocked for ${htsUserSchedule.nino.value}")
        } else {
          logger.debug(s"A request to unblock for Email is returned with error for $url")
          logger.warn(s"Request to unblock email failed for ${htsUserSchedule.nino.value}")
        }
    } yield Ok).fold(identity, identity)
  }
}
