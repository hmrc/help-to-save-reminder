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

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

object DateTimeFunctions {

  val sixtyTwoDays: Int = LocalDate.parse("2020-06-01").until(LocalDate.parse("2020-08-01"), DAYS).toInt

  def getNextSendDate(daysToReceive: Seq[Int], today: LocalDate): Option[LocalDate] =
    (1 to sixtyTwoDays)
      .map(today.plusDays(_))
      .find(x => daysToReceive.contains(x.getDayOfMonth))

}
