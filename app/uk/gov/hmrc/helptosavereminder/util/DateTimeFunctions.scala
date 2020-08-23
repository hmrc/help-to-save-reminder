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

  def getNextSendDate(daysToReceive: Seq[Int], localDateParam: LocalDate): LocalDate = {

    val maxDaysInMonth = localDateParam.getMonth.maxLength();
    val currentDayOfMonth = localDateParam.getDayOfMonth
    val validDaysToReceive = daysToReceive.filter(x => x <= maxDaysInMonth)
    val nextAvailableDayOfMonth = validDaysToReceive.filter(x => x > currentDayOfMonth).headOption

    nextAvailableDayOfMonth match {
      case Some(day) => localDateParam.plusDays(day - currentDayOfMonth)
      case None => {
        daysToReceive.sorted.headOption match {
          case Some(usersFirstChoice) =>
            localDateParam
              .plusMonths(1)
              .withDayOfMonth(validDaysToReceive.headOption.getOrElse(usersFirstChoice))
        }
      }
    }

  }

  private def getMaxDaysInMonth =
    (YearMonth
      .of(Calendar.getInstance.get(Calendar.YEAR), Calendar.getInstance.get(Calendar.MONTH) + 1))
      .lengthOfMonth()

}
