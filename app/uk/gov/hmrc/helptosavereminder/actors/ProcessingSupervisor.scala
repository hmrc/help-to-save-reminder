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
import uk.gov.hmrc.helptosavereminder.models.SendEmails
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, TimePeriodLockService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import java.util.TimeZone
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success}

@Singleton
class ProcessingSupervisor @Inject() (
  mongoApi: MongoComponent,
  servicesConfig: ServicesConfig,
  emailConnector: EmailConnector,
  lockrepo: MongoLockRepository
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends Actor with Logging {

  lazy val repository = new HtsReminderMongoRepository(mongoApi)
  lazy val emailSenderActor = new EmailSenderActor(servicesConfig, repository, emailConnector)

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

    case BOOTSTRAP =>
      if (isUserScheduleEnabled && CronExpression.isValidExpression(userScheduleCronExpression)) {
        val scheduler = QuartzSchedulerExtension(context.system)
        scheduler
          .createSchedule(
            "UserScheduleJob",
            Some("For sending reminder emails to the users"),
            userScheduleCronExpression,
            timezone = TimeZone.getTimeZone("Europe/London")
          )
        scheduler.schedule("UserScheduleJob", self, START)
        logger.info(s"[ProcessingSupervisor] Scheduler started")
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
                emailSenderActor.sendScheduleMsg(request, currentDate).onComplete {
                  case Failure(exception) => logger.error(s"Failed to send an e-mail to ${request.email}", exception)
                  case Success(())        => self ! Acknowledge(request.email)
                }

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
