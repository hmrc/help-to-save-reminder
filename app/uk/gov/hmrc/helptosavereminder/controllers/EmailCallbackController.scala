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
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext

class EmailCallbackController @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig,
  val cc: MessagesControllerComponents,
  repository: HtsReminderMongoRepository)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def handleCallBack(callBackReference: String) = Action.async { implicit request =>
    val nino = callBackReference.takeRight(9)
    repository.findByNino(nino).flatMap { htsUser =>
      repository.deleteHtsUser(nino).map {
        case Left(error) => NotModified
        case Right(()) =>
          val url = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/${htsUser.get.email}"
          http.DELETE(url, Seq(("Content-Type", "application/json"))) map { response =>
            response.status match {

              case 200 => Logger.debug(s"[EmailCallbackController] Email deleted: ${response.body}");
              case _   => Logger.error(s"[EmailCallbackController] Email not deleted: ${response.body}");

            }
          }
          Ok
      }
    }
  }

}
