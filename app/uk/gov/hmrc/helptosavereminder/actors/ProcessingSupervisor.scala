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

package uk.gov.hmrc.helptosavereminder.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.quartz.CronExpression
import play.api.Logging
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.models.{HtsUserScheduleMsg, SendEmails}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, TimePeriodLockService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import java.util.TimeZone
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, DurationInt}

@Singleton
class ProcessingSupervisor @Inject() (
  mongoApi: MongoComponent,
  servicesConfig: ServicesConfig,
  emailConnector: EmailConnector,
  lockrepo: MongoLockRepository
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends Actor with Logging {

  lazy val repository = new HtsReminderMongoRepository(mongoApi)

  lazy val emailSenderActor: ActorRef =
    context.actorOf(
      Props(classOf[EmailSenderActor], servicesConfig, repository, emailConnector, ec, appConfig),
      "emailSender-actor"
    )

  private lazy val testOnlyActor: ActorRef =
    if (servicesConfig.getBoolean("testActorEnabled")) {
      context.actorOf(Props(classOf[TestOnlyActor], repository), "test-only-actor")
    } else {
      context.actorOf(Props(classOf[EmptyActor]), "test-only-actor")
    }

  lazy val isUserScheduleEnabled: Boolean = appConfig.isUserScheduleEnabled

  lazy val userScheduleCronExpression: String = appConfig.userScheduleCronExpression.replace('|', ' ')

  val defaultRepoLockPeriod: Int = appConfig.defaultRepoLockPeriod

  lazy val repoLockPeriod: Int = appConfig.repoLockPeriod

  val scheduleTake = appConfig.scheduleTake

  val lockId: String = "emailProcessing"
  val lockDuration = Duration.fromNanos(repoLockPeriod)
  val lockKeeper: TimePeriodLockService = TimePeriodLockService(lockrepo, lockId, lockDuration)

  override def receive: Receive = {

    case BOOTSTRAP => {

      logger.info("[ProcessingSupervisor] BOOTSTRAP UserSchedule Quartz Scheduler processing started")

      val scheduler = QuartzSchedulerExtension(context.system)
      val isExpressionValid = CronExpression.isValidExpression(userScheduleCronExpression)

      repository.collection.find().toFuture().map {
        case requests if requests.nonEmpty => {
          val nextScheduledDates = requests.map(request => request.nextSendDate).toSet
          val daysToRecieve = requests.map(request => request.daysToReceive).toSet
          val emailDuplicateOccurrencesSet =
            requests.groupBy(_.email).view.mapValues(_.size).groupBy(_._2).view.mapValues(_.size)

          logger.info(s"[ProcessingSupervisor][BOOTSTRAP] found ${requests.size} requests")
          logger.info(
            s"[ProcessingSupervisor][BOOTSTRAP] found ${requests.map(request => request.email).toSet.size} unique emails"
          )

          logger.info(
            s"[ProcessingSupervisor][BOOTSTRAP] found ${emailDuplicateOccurrencesSet.mkString(", ")} emailsOccurrences[Duplicates ,Occurrences]"
          )

          logger.info(s"[ProcessingSupervisor][BOOTSTRAP] found ${nextScheduledDates.mkString(", ")} [nextSendDates]")
          nextScheduledDates.foreach(
            date =>
              logger.info(
                s"[ProcessingSupervisor][BOOTSTRAP] found ${requests.count(request => request.nextSendDate == date)} [nextSendDate : $date]"
              )
          )

          logger.info(s"[ProcessingSupervisor][BOOTSTRAP] found ${daysToRecieve.mkString(", ")} [daysToReceive]")
          daysToRecieve.foreach(
            days =>
              logger.info(s"[ProcessingSupervisor][BOOTSTRAP] found ${requests
                .count(usr => usr.daysToReceive == days)} Set to ${days.mkString(", ")}")
          )
        }

        case _ => {
          logger.info(s"[ProcessingSupervisor][BOOTSTRAP] found no requests found")
        }
      }

      (isUserScheduleEnabled, isExpressionValid) match {
        case (true, true) =>
          logger.info(
            s"[ProcessingSupervisor] BOOTSTRAP is scheduled with userScheduleCronExpression = $userScheduleCronExpression"
          )
          scheduler
            .createSchedule(
              "UserScheduleJob",
              Some("For sending reminder emails to the users"),
              userScheduleCronExpression,
              timezone = TimeZone.getTimeZone("Europe/London")
            )
          scheduler.schedule("UserScheduleJob", self, START)

        case (_, false) =>
          logger.warn(
            s"UserScheduleJob cannot be Scheduled due to invalid cronExpression supplied in configuration : $userScheduleCronExpression"
          )

        case _ =>
          logger.warn(
            s"UserScheduleJob cannot be Scheduled. Please check configuration parameters: " +
              s"userScheduleCronExpression = $userScheduleCronExpression and isUserScheduleEnabled = $isUserScheduleEnabled"
          )
      }
    }

    case START => {
      testOnlyActor ! CLEAR

      logger.info(s"START message received by ProcessingSupervisor and forceLockReleaseAfter = $repoLockPeriod")

      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))

      lockKeeper
        .withRenewedLock {

          repository.findHtsUsersToProcess().map {
            case Some(requests) if requests.nonEmpty => {
              logger.info(s"[ProcessingSupervisor][receive] took ${requests.size} requests)")

              val take = requests.take(scheduleTake)
              logger.info(s"[ProcessingSupervisor][receive] but only taking ${take.size} requests)")

              for (request <- take) {
                testOnlyActor ! Init(request.email)
                emailSenderActor ! HtsUserScheduleMsg(request, currentDate)

              }

            }
            case _ => {
              logger.info(s"[ProcessingSupervisor][receive] no requests pending")
            }
          }
        }
        .map {
          case Some(thing) => {

            logger.info(s"[ProcessingSupervisor][receive] OBTAINED mongo lock")
            testOnlyActor ! SUCCESS
          }
          case _ => {
            logger.info(s"[ProcessingSupervisor][receive] failed to OBTAIN mongo lock.")
            testOnlyActor ! FAILURE
          }
        }

      logger.info("Exiting START message processor by ProcessingSupervisor")

    }

    case Acknowledge(email) => testOnlyActor ! Acknowledge(email)
    case GET_STATS =>
      implicit val timeout: Timeout = Timeout(5.seconds)
      testOnlyActor ? GET_STATS pipeTo sender()
    case SendEmails(emails) => testOnlyActor ! SendEmails(emails)
  }

}
