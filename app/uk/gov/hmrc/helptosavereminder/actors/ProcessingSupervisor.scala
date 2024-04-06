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

import akka.actor.Actor
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.quartz.CronExpression
import play.api.Logging
import uk.gov.hmrc.helptosavereminder.config.AppConfig
import uk.gov.hmrc.helptosavereminder.connectors.EmailConnector
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.models.SendEmails
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, ZoneId}
import java.util.TimeZone
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

@Singleton
class ProcessingSupervisor @Inject() (
  mongoApi: MongoComponent,
  servicesConfig: ServicesConfig,
  emailConnector: EmailConnector,
  lockrepo: MongoLockRepository
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends Actor with Logging {

  lazy val repository = new HtsReminderMongoRepository(mongoApi)
  lazy val emailSenderActor = new EmailSenderActor(servicesConfig, repository, emailConnector, lockrepo)

  private lazy val testOnlyActor: StatCollector =
    if (servicesConfig.getBoolean("testActorEnabled")) new TestOnlyActor(repository)
    else new EmptyActor

  lazy val userScheduleCronExpression: String = appConfig.userScheduleCronExpression.replace('|', ' ')

  lazy val repoLockPeriod: Int = appConfig.repoLockPeriod

  val scheduleTake: Int = appConfig.scheduleTake
  val lockService = LockService(lockrepo, lockId = "emailProcessing", ttl = repoLockPeriod.seconds)

  override def receive: Receive = {

    case BOOTSTRAP =>
      if (CronExpression.isValidExpression(userScheduleCronExpression)) {
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
      testOnlyActor.clear()
      logger.info(s"START message received by ProcessingSupervisor and forceLockReleaseAfter = $repoLockPeriod")
      val currentDate = LocalDate.now(ZoneId.of("Europe/London"))
      lockService withLock {
        repository.findHtsUsersToProcess().flatMap {
          case None | Some(Nil) =>
            logger.info(s"[ProcessingSupervisor][receive] no requests pending")
            Future.successful(List())
          case Some(requests) =>
            val take = requests.take(scheduleTake)
            logger
              .info(s"[ProcessingSupervisor][receive] ${requests.size} found but only taking ${take.size} requests)")
            take
              .map { request =>
                testOnlyActor.init(request.email)
                emailSenderActor
                  .sendScheduleMsg(request, currentDate)
                  .map { _ =>
                    testOnlyActor.acknowledge(request.email)
                    Right(request.email)
                  }
                  .recover { exception =>
                    logger.error(s"Failed to send an e-mail to ${request.email}", exception)
                    Left(request.email)
                  }
              }
              .pipe(Future.sequence(_))
        }
      } foreach {
        case Some(thing) =>
          logger.info(s"[ProcessingSupervisor][receive] OBTAINED mongo lock")
          testOnlyActor.signalSuccess()
        case _ =>
          logger.info(s"[ProcessingSupervisor][receive] failed to OBTAIN mongo lock.")
          testOnlyActor.signalFailure()
      }
      logger.info("Exiting START message processor by ProcessingSupervisor")
    }

    case GET_STATS          => sender() ! testOnlyActor.stats
    case SendEmails(emails) => testOnlyActor.generateRecipients(emails)
  }

}
