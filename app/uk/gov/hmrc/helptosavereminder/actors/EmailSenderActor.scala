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

package uk.gov.hmrc.helptosavereminder.actors

import akka.actor._
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavereminder.models.{HtsReminderTemplate, Reminder, SendTemplatedEmailRequest, UpdateCallBackRef, UpdateCallBackSuccess}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailSenderActor @Inject()(
  http: HttpClient,
  environment: Environment,
  val runModeConfiguration: Configuration,
  servicesConfig: ServicesConfig,
  repository: HtsReminderMongoRepository)(implicit ec: ExecutionContext)
    extends Actor {

  implicit lazy val hc = HeaderCarrier()
  lazy val htsUserUpdateActor: ActorRef =
    context.actorOf(
      Props(classOf[HtsUserUpdateActor], http, environment, runModeConfiguration, servicesConfig, repository, ec),
      "htsUserUpdate-actor")
  lazy val sendEmailTemplateId = runModeConfiguration.get[String]("microservice.services.email.templateId")
  lazy val nameParam = runModeConfiguration.get[String]("microservice.services.email.nameParam")
  lazy val callBackUrlParam = runModeConfiguration.get[String]("microservice.services.email.callBackUrlParam")

  override def receive: Receive = {

    case htsUserReminder: Reminder => {

      val callBackRef = System.currentTimeMillis().toString + htsUserReminder.nino
      htsUserUpdateActor ! UpdateCallBackRef(htsUserReminder, callBackRef)

    }

    case successReminder: UpdateCallBackSuccess => {

      Logger.info("Received the DB Update Success for " + successReminder.reminder.nino)

      val template =
        HtsReminderTemplate(
          successReminder.reminder.email.value,
          successReminder.reminder.name,
          successReminder.reminder.callBackUrlRef)

      sendReceivedTemplatedEmail(template).map({
        case true => {
          htsUserUpdateActor ! successReminder.reminder
        }
        case false =>
      })

    }

  }

  def sendReceivedTemplatedEmail(template: HtsReminderTemplate)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val callBackUrl = s"${servicesConfig.baseUrl("help-to-save-reminder")}/hmrc/bouncedEmail/" + template.callBackUrlRef

    println("THE BOUNCEBACK URL IS :  " + callBackUrl)

    val request = SendTemplatedEmailRequest(
      List(template.email),
      sendEmailTemplateId,
      Map(nameParam -> template.name, callBackUrlParam -> callBackUrl))

    sendEmail(request)

  }

  private def sendEmail(request: SendTemplatedEmailRequest)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val url = s"${servicesConfig.baseUrl("email")}/hmrc/email"

    println("THE URL for sending email TO :  " + url)

    http.POST(url, request, Seq(("Content-Type", "application/json"))) map { response =>
      response.status match {

        case 202 => Logger.debug(s"[EmailSenderActor] Email sent: ${response.body}"); true
        case _   => Logger.error(s"[EmailSenderActor] Email not sent: ${response.body}"); false

      }
    }
  }
}
