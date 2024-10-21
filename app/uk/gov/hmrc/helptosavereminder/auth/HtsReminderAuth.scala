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

import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino => v2Nino}
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

  private type HtsActionWithNINO = Request[AnyContent] => NINO => Future[Result]

  def ggAuthorisedWithNino(action: HtsActionWithNINO)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthWithCL200)
        .retrieve(v2Nino) { mayBeNino =>
          mayBeNino.fold[Future[Result]] {
            logger.warn("Could not find NINO for logged in user")
            Forbidden
          }(nino => action(request)(nino))
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
