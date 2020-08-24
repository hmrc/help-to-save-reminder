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

import java.time.{LocalDate, Month}

object DateTimeFunctions {

  def filterValidDays(days: Seq[Int], month: Month) : Seq[Int] =
    days.filter(x => x >= 1 && x <= month.maxLength())

  def getNextSendDate(daysToReceive: Seq[Int], today: LocalDate): LocalDate = {
    val sortedDays = daysToReceive.sorted
    val laterDays = sortedDays.filter(x => x > today.getDayOfMonth)
    filterValidDays(laterDays, today.getMonth) match {
      case day :: _ => today.withDayOfMonth(day)
      case _ => {
        val laterMonth = today.plusMonths(1)
        filterValidDays(sortedDays, laterMonth.getMonth) match {
          case day :: _ => laterMonth.withDayOfMonth(day)
          case _ => throw new Exception(s"No next send date for $daysToReceive from $today")
        }
       }
    }

  }
}
