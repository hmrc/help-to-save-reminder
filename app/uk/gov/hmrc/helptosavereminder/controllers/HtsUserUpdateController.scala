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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.controllers.HtsReminderAuth
import uk.gov.hmrc.helptosavereminder.models.{CancelHtsUserReminder, HtsUser, UpdateEmail}
import uk.gov.hmrc.helptosavereminder.repo.{HtsReminderMongoRepository, HtsReminderRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HtsUserUpdateController @Inject()(
  repository: HtsReminderRepository,
  cc: ControllerComponents,
  override val authConnector: AuthConnector)(implicit val ec: ExecutionContext)
    extends HtsReminderAuth(authConnector, cc) {

  def update(): Action[AnyContent] = ggAuthorisedWithNino { implicit request => implicit nino ⇒
    request.body.asJson.get
      .validate[HtsUser]
      .fold(
        error => {
          Logger.error(s"Unable to de-serialise request as a HtsUser: ${error.mkString}")
          Future.successful(BadRequest)
        },
        (htsUser: HtsUser) => {
          Logger.info(s"The HtsUser received from frontend to update is : " + htsUser)
          repository.updateReminderUser(htsUser).map {
            case true  => Ok(Json.toJson(htsUser))
            case false => NotModified
          }
        }
      )
  }

  def getIfHtsUserExists(nino: String) = Action.async { implicit request =>
    repository.findByNino(nino).map {
      case Some(htsUser) => {
        Ok(Json.toJson(htsUser))
      }

      case None => {
        NotFound
      }
    }
  }

  def deleteHtsUser(): Action[AnyContent] = ggAuthorisedWithNino { implicit request => implicit nino ⇒
    request.body.asJson.get
      .validate[CancelHtsUserReminder]
      .fold(
        error => {
          Logger.error(s"Unable to de-serialise request as a CancelHtsUserReminder: ${error.mkString}")
          Future.successful(BadRequest)
        },
        (userReminder: CancelHtsUserReminder) => {
          Logger.info(s"The HtsUser received from frontend to delete is : " + userReminder)
          repository.deleteHtsUser(userReminder.nino).map {
            case Left(error) => NotModified
            case Right(())   => Ok
          }
        }
      )
  }

  def updateEmail(): Action[AnyContent] = ggAuthorisedWithNino { implicit request => implicit nino ⇒
      request.body.asJson.get
        .validate[UpdateEmail]
        .fold(
          error => {
            Logger.error(s"Unable to de-serialise request as a HtsUser: ${error.mkString}")
            Future.successful(BadRequest)
          },
          (userRequest: UpdateEmail) => {
            repository.updateEmail(userRequest.nino.nino, userRequest.name, userRequest.email).map {
              case true => {
                Ok
              }
              case false => {
                NotFound
              }
            }
          }
        )
    }

}
