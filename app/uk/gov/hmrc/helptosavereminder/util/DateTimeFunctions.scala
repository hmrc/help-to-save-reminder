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

package uk.gov.hmrc.helptosavereminder.util

import java.time.{Duration, LocalDate, LocalDateTime, YearMonth, ZoneId}
import java.util.Calendar

object DateTimeFunctions {

  def getNextSendDate(daysToReceive: Seq[Int]): LocalDate = {

    val maxDaysInMonth = getMaxDaysInMonth
    val validDaysToReceive = daysToReceive.filter(x => x <= maxDaysInMonth)
    val currentDayOfMonth = Calendar.getInstance.get(Calendar.DAY_OF_MONTH)
    val nextAvailableDayOfMonth = validDaysToReceive.filter(x => x > currentDayOfMonth).headOption

    nextAvailableDayOfMonth match {
      case Some(day) => (LocalDate.now).plusDays(day - currentDayOfMonth)
      case None      => (LocalDate.now).plusMonths(1).withDayOfMonth(validDaysToReceive.head)
    }

  }

  def getNextSchedule(scheduledDays: String, scheduledTimes: String): Long = {

    val scheduledDaysList = scheduledDays.split(",").toList.map(x => x.toInt)
    val scheduledTimesList = scheduledTimes.split(",").toList
    val maxDaysInMonth = getMaxDaysInMonth
    val scheduledDaysFiltered = scheduledDaysList.filter(x => x <= maxDaysInMonth)

    val mapOfScheduledTimes: Seq[(Int, Int, Int)] =
      scheduledDaysFiltered.flatMap(day =>
        scheduledTimesList.map(timePoint =>
          (day, timePoint.split(':').toList(0).toInt, timePoint.split(':').toList(1).toInt)))

    val scheduledTimesFinalized = mapOfScheduledTimes.map(
      x =>
        (LocalDate.now)
          .plusMonths(0)
          .withDayOfMonth(x._1.toInt)
          .atStartOfDay()
          .plusHours(x._2)
          .plusMinutes(x._3)
    )

    val bstAdjustedTimes = scheduledTimesFinalized.map(x => if (isSummerTime(x)) x.minusHours(1) else x)

    val nextTimeSlot = bstAdjustedTimes.find(x => x.isAfter(LocalDateTime.now()))

    nextTimeSlot match {
      case Some(slot) => {

        Duration.between(LocalDateTime.now(), slot).toNanos

      }
      case None => {

        val timeTuples = mapOfScheduledTimes.apply(0)
        val nextMonthSlot = (LocalDate.now)
          .plusMonths(1)
          .withDayOfMonth(timeTuples._1)
          .atStartOfDay()
          .plusHours(timeTuples._2.toInt)
          .plusMinutes(timeTuples._3.toInt)

        val bstAdjustedNextMonthSlot = if (isSummerTime(nextMonthSlot)) nextMonthSlot.minusHours(1) else nextMonthSlot

        Duration.between(LocalDateTime.now(), bstAdjustedNextMonthSlot).toNanos

      }
    }
  }

  private def getMaxDaysInMonth =
    (YearMonth
      .of(Calendar.getInstance.get(Calendar.YEAR), Calendar.getInstance.get(Calendar.MONTH) + 1))
      .lengthOfMonth()

  private def isSummerTime(dateTimeToCheck: LocalDateTime): Boolean = {

    val ukTz = ZoneId.of("Europe/London")
    val zonedTimeSeconds = dateTimeToCheck.atZone(ukTz).getOffset.getTotalSeconds

    if (zonedTimeSeconds > 0) {
      true
    } else
      false

  }
}
