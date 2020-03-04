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

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneOffset}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.models.Schedule
import uk.gov.hmrc.helptosavereminder.repo.{HtsReminderMongoRepository, SchedulerMongoRepository, SchedulerRepository}
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

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

  lazy val schedulerRepository = new SchedulerMongoRepository(mongoApi)

  lazy val emailSenderActor: ActorRef =
    context.actorOf(
      Props(classOf[EmailSenderActor], httpClient, env, config, servicesConfig, repository, ec),
      "emailSender-actor")

  val lockrepo = LockMongoRepository(mongoApi.mongoConnector.db)

  val scheduledDays = config.get[String]("scheduledDays")

  val scheduledTimes = config.get[String]("scheduledTimes")

  val lockKeeper = new LockKeeper {

    override def repo: LockRepository = lockrepo //The repo created before

    override def lockId: String = "emailProcessing"

    override val forceLockReleaseAfter: org.joda.time.Duration = org.joda.time.Duration.standardMinutes(1)

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

  private def getNextDelayInNanos() = DateTimeFunctions.getNextSchedule(scheduledDays, scheduledTimes)

  override def receive: Receive = {

    case STOP => {
      Logger.debug("[ProcessingSupervisor] received while not processing: STOP received")
      lockrepo.releaseLock(lockKeeper.lockId, lockKeeper.serverId)
    }

    case BOOTSTRAP => {

      Logger.info(
        "[ProcessingSupervisor] BOOTSTRAP processing started and the next schedule is at : " + LocalDateTime
          .now()
          .plusNanos(getNextDelayInNanos()))

      context.system.scheduler.scheduleOnce(getNextDelayInNanos() nanos, self, START)

    }

    case START => {

      val scheduleKickOffTime = LocalDateTime.now()

      lockKeeper
        .tryLock {

          repository.findHtsUsersToProcess().map {
            case Some(requests) if requests.nonEmpty => {
              Logger.info(s"[ProcessingSupervisor][receive] took ${requests.size} request/s")

              for (request <- requests) {

                emailSenderActor ! request

              }

            }
            case _ => {
              Logger.info(s"[ProcessingSupervisor][receive] no requests pending")
            }
          }

        }
        .map {
          case Some(thing) => {

            Logger.info(s"[ProcessingSupervisor][receive] OBTAINED mongo lock")

          }
          case _ => {
            Logger.info(
              s"[ProcessingSupervisor][receive] failed to OBTAIN mongo lock. Scheduling for next available slot at " + LocalDateTime
                .now()
                .plusNanos(getNextDelayInNanos()))
            context.system.scheduler.scheduleOnce(getNextDelayInNanos() nanos, self, START)
          }

        }

      val nextInNanos = getNextDelayInNanos()
      val nextScheduledAt = LocalDateTime.now().plusNanos(nextInNanos)
      val scheduleToSave = Schedule(scheduleKickOffTime, nextScheduledAt)

      schedulerRepository.createSchedule(scheduleToSave).map {
        case Left(error) => Logger.info("Error occurred while writing the schedule details : " + scheduleToSave)
        case Right(x)    => Logger.info("Scheduled saved to DB = " + x)
      }

      context.system.scheduler.scheduleOnce(nextInNanos nanos, self, START)

    }
  }

}
