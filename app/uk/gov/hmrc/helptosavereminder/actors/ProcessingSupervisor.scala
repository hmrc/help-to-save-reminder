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

import java.util.TimeZone

import akka.actor.{Actor, ActorRef, Props}
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import uk.gov.hmrc.http.HttpClient
import org.quartz.CronExpression

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class ProcessingSupervisor @Inject()(
  mongoApi: play.modules.reactivemongo.ReactiveMongoComponent,
  config: Configuration,
  httpClient: HttpClient,
  env: Environment,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext)
    extends Actor {

  lazy val repository = new HtsReminderMongoRepository(mongoApi)

  lazy val emailSenderActor: ActorRef =
    context.actorOf(
      Props(classOf[EmailSenderActor], httpClient, env, config, servicesConfig, repository, ec),
      "emailSender-actor")

  val lockrepo = LockMongoRepository(mongoApi.mongoConnector.db)

  lazy val isUserScheduleEnabled: Boolean = config.getOptional[Boolean](s"isUserScheduleEnabled").getOrElse(false)

  lazy val userScheduleCronExpression: String = config.getOptional[String](s"userScheduleCronExpression").getOrElse("")

  val lockKeeper = new LockKeeper {

    override def repo: LockRepository = lockrepo //The repo created before

    override def lockId: String = "emailProcessing"

    override val forceLockReleaseAfter: org.joda.time.Duration = org.joda.time.Duration.standardMinutes(55)

    // $COVERAGE-OFF$
    override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
      repo
        .lock(lockId, serverId, forceLockReleaseAfter)
        .flatMap { acquired =>
          if (acquired) {
            body.map { case x => Some(x) }
          } else Future.successful(None)
        }
        .recoverWith { case ex => repo.releaseLock(lockId, serverId).flatMap(_ => Future.failed(ex)) }
    // $COVERAGE-ON$
  }

  override def receive: Receive = {

    case STOP => {
      Logger.debug("[ProcessingSupervisor] received while not processing: STOP received")
      lockrepo.releaseLock(lockKeeper.lockId, lockKeeper.serverId)
    }

    case BOOTSTRAP => {

      Logger.info("[ProcessingSupervisor] BOOTSTRAP UserSchedule Quartz Scheduler processing started")

      val scheduler = QuartzSchedulerExtension(context.system)
      val isExpressionValid = CronExpression.isValidExpression(userScheduleCronExpression)

      (isUserScheduleEnabled, isExpressionValid) match {
        case (true, true) =>
          scheduler
            .createSchedule(
              "UserScheduleJob",
              Some("For sending reminder emails to the users"),
              userScheduleCronExpression,
              timezone = TimeZone.getTimeZone("Europe/London"))
          scheduler.schedule("UserScheduleJob", self, START)

        case (_, false) =>
          Logger.warn(s"UserScheduleJob cannot be Scheduled due to invalid cronExpression supplied in configuration : $userScheduleCronExpression")

        case _ =>
          Logger.warn(s"UserScheduleJob cannot be Scheduled. Please check configuration parameters: " +
            s"userScheduleCronExpression = $userScheduleCronExpression and isUserScheduleEnabled = $isUserScheduleEnabled")
      }
    }

    case START => {

      lockKeeper
        .tryLock {

          repository.findHtsUsersToProcess().map {
            case Some(requests) if requests.nonEmpty => {
              Logger.debug(s"[ProcessingSupervisor][receive] took ${requests.size} request/s")

              for (request <- requests) {

                emailSenderActor ! request

              }

            }
            case _ => {
              Logger.debug(s"[ProcessingSupervisor][receive] no requests pending")
            }
          }

        }
        .map {
          case Some(thing) => {

            Logger.info(s"[ProcessingSupervisor][receive] OBTAINED mongo lock")

          }
          case _ => {
            Logger.info(s"[ProcessingSupervisor][receive] failed to OBTAIN mongo lock.")
          }
        }

    }
  }

}
