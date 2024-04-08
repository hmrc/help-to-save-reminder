/*
 * Copyright 2024 HM Revenue & Customs
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

import akka.actor.{Actor, Props}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.quartz.CronExpression

import java.util.TimeZone

// Actor to run a function on a Cron schedule

object Scheduler {
  val RUN = "RUN"
  def props(cronExpression: String, callback: () => Unit): Props = {
    if (!CronExpression.isValidExpression(cronExpression))
      throw new RuntimeException(s"'$cronExpression' is not a valid Cron expression")
    Props(new Scheduler(cronExpression, callback))
  }
}

class Scheduler(cronExpression: String, callback: () => Unit) extends Actor {
  private val scheduler = QuartzSchedulerExtension(context.system)
  scheduler
    .createSchedule(
      "UserScheduleJob",
      Some("For sending reminder emails to the users"),
      cronExpression,
      timezone = TimeZone.getTimeZone("Europe/London")
    )
  scheduler.schedule("UserScheduleJob", self, Scheduler.RUN)

  def receive: Receive = {
    case Scheduler.RUN => callback()
  }

  override def postStop(): Unit =
    scheduler.shutdown(false)
  super.postStop()
}
