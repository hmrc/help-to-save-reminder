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

package uk.gov.hmrc.helptosavereminder.connectors

import play.api.Logging
import play.api.http.Status.{ACCEPTED, OK}
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest.format
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject() (http: HttpClient, servicesConfig: ServicesConfig) extends Logging {
  val sendEmailUrl = s"${servicesConfig.baseUrl("email")}/hmrc/email"
  val unBlockEmailUrl = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/"

  def sendEmail(request: SendTemplatedEmailRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    http.POST(sendEmailUrl, request, Seq(("Content-Type", "application/json")))(format, readRaw, hc, ec) map {
      response =>
        response.status match {
          case ACCEPTED => true
          case _        => logger.error(s"[EmailConnector] Email not sent: ${response.status}"); false
        }
    }

  def unBlockEmail(email: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    http.DELETE(s"$unBlockEmailUrl$email", Seq(("Content-Type", "application/json")))(readRaw, hc, ec) map { response =>
      response.status match {
        case OK | ACCEPTED => true
        case _             => logger.error(s"[EmailConnector] Email not unblocked: ${response.status}"); false
      }
    }
}
