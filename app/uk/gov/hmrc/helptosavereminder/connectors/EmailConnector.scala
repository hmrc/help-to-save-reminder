/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.instances.int._
import cats.syntax.eq._
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status.{ACCEPTED, OK}
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest
import uk.gov.hmrc.helptosavereminder.models.SendTemplatedEmailRequest.format
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject() (http: HttpClient) extends Logging {

  def sendEmail(
    request: SendTemplatedEmailRequest,
    url: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    http.POST(url, request, Seq(("Content-Type", "application/json")))(format, readRaw, hc, ec) map { response =>
      response.status match {
        case ACCEPTED =>
          logger.debug(s"[EmailSenderActor] Email sent: ${response.body}"); true
        case _ => logger.error(s"[EmailSenderActor] Email not sent: ${response.status}"); false
      }
    }

  def unBlockEmail(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    http.DELETE(url, Seq(("Content-Type", "application/json")))(readRaw, hc, ec) map { response =>
      response.status match {
        case x if x === OK || x === ACCEPTED =>
          logger.debug(s"Email is successfully unblocked: ${response.body}"); true
        case _ =>
          logger.warn(s"[EmailSenderActor] Email not unblocked: ${response.status}"); false
      }
    }
}
