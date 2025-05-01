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
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest.format
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject() (http: HttpClientV2, servicesConfig: ServicesConfig) extends Logging {
  private val sendEmailUrl = s"${servicesConfig.baseUrl("email")}/hmrc/email"
  private val unBlockEmailUrl = s"${servicesConfig.baseUrl("email")}/hmrc/bounces/"

  def sendEmail(request: SendTemplatedEmailRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    for {
      response <- http
                    .post(url"$sendEmailUrl")
                    .withBody(Json.toJson(request))
                    .setHeader("Content-Type" -> "application/json")
                    .execute[Either[UpstreamErrorResponse, HttpResponse]]
    } yield response match {
      case Right(response) if response.status == ACCEPTED => true
      case Right(response) =>
        logger.error(s"[EmailConnector] Email not sent: ${response.status}"); false
      case Left(errorResponse) =>
        logger.error(s"[EmailConnector] Email not sent: ${errorResponse.statusCode}"); false
    }

  def unBlockEmail(email: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    for {
      response <- http
                    .delete(url"$unBlockEmailUrl$email")
                    .setHeader("Content-Type" -> "application/json")
                    .execute[Either[UpstreamErrorResponse, HttpResponse]]
    } yield response match {
      case Right(response) if response.status == OK || response.status == ACCEPTED => true
      case Right(response) =>
        logger.error(s"[EmailConnector] Email not unblocked: ${response.status}"); false
      case Left(errorResponse) =>
        logger.error(s"[EmailConnector] Email not unblocked: ${errorResponse.statusCode}"); false
    }
}
