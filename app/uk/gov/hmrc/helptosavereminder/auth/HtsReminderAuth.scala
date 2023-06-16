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

package uk.gov.hmrc.helptosavereminder.auth

import cats.instances.string._
import cats.syntax.eq._
import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino => v2Nino}
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, PAClientId, v2}
import uk.gov.hmrc.helptosave.util.{NINO, toFuture}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

object HtsReminderAuth {

  val GGProvider: AuthProviders = AuthProviders(GovernmentGateway)

  val GGAndPrivilegedProviders: Predicate = AuthProviders(GovernmentGateway, PrivilegedApplication)

  val AuthWithCL200: Predicate = GGProvider and ConfidenceLevel.L200

}

class HtsReminderAuth(htsAuthConnector: AuthConnector, controllerComponents: ControllerComponents)
    extends BackendController(controllerComponents) with AuthorisedFunctions with Logging {

  import HtsReminderAuth._

  override def authConnector: AuthConnector = htsAuthConnector

  private type HtsAction = Request[AnyContent] => Future[Result]
  private type HtsActionWithNINO = Request[AnyContent] => NINO => Future[Result]

  val authProviders: AuthProviders = AuthProviders(GovernmentGateway, PrivilegedApplication)

  def ggAuthorisedWithNino(action: HtsActionWithNINO)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthWithCL200)
        .retrieve(v2Nino) {
          case None =>
            logger.warn("Could not find NINO for logged in user")
            Forbidden
          case Some(nino) => action(request)(nino)
        }
        .recover {
          handleFailure()
        }
    }

  def ggOrPrivilegedAuthorised(action: HtsAction)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(GGAndPrivilegedProviders) {
        action(request)
      }.recover {
        handleFailure()
      }
    }

  def ggOrPrivilegedAuthorisedWithNINO(
    nino: Option[String]
  )(action: HtsActionWithNINO)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(GGAndPrivilegedProviders)
        .retrieve(v2.Retrievals.authProviderId) {
          /* authProviderId deprecated but credentials does not support Privileged Application client*/
          case GGCredId(_) =>
            authorised().retrieve(v2Nino) { retrievedNINO =>
              (nino, retrievedNINO) match {
                case (Some(given), Some(retrieved)) =>
                  if (given === retrieved) {
                    action(request)(given)
                  } else {
                    logger.warn("Given NINO did not match retrieved NINO")
                    toFuture(Forbidden)
                  }

                case (None, Some(retrieved)) =>
                  action(request)(retrieved)

                case (_, None) =>
                  logger.warn("Could not retrieve NINO for GG session")
                  Forbidden
              }
            }

          case PAClientId(_) =>
            nino match {
              case None =>
                logger.warn("NINO not given for privileged request")
                BadRequest
              case Some(n) => action(request)(n)
            }

          case other =>
            logger.warn(s"Recevied request from unsupported authProvider: ${other.getClass.getSimpleName}")
            toFuture(Forbidden)

        }
        .recover {
          handleFailure()
        }
    }

  def handleFailure(): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      logger.warn("user is not logged in, probably a hack?")
      Unauthorized

    case e: InternalError =>
      logger.warn(s"Could not authenticate user due to internal error: ${e.reason}")
      InternalServerError

    case ex: AuthorisationException =>
      logger.warn(s"could not authenticate user due to: ${ex.reason}")
      Forbidden
  }

}
