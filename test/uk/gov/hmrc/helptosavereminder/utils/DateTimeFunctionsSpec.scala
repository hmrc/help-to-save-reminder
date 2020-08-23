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

package uk.gov.hmrc.helptosavereminder.utils

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

import java.time.{LocalDate, ZoneId}
import java.util.Calendar

import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions

class DateTimeFunctionsSpec extends WordSpec with Matchers with GuiceOneAppPerSuite {

  val lastDayOftheMonth = Calendar.getInstance.getActualMaximum(Calendar.DAY_OF_MONTH);

  val monthsList = List(
    "JANUARY",
    "FEBRUARY",
    "MARCH",
    "APRIL",
    "MAY",
    "JUNE",
    "JULY",
    "AUGUST",
    "SEPTEMBER",
    "OCTOBER",
    "NOVEMBER",
    "DECEMBER")

  "DateTimeFunctions object " should {
    "return appropriate day " in {

      val localDateParam = LocalDate.now(ZoneId.of("Europe/London"))
      val startOfMonth = localDateParam.withDayOfMonth(1)
      val nextMonthFirstDay = startOfMonth.plusMonths(1)

      val thisMonthIndex = monthsList.indexOf(localDateParam.getMonth.toString)
      val dateResult1 = DateTimeFunctions.getNextSendDate(Seq(1), localDateParam)
      monthsList((thisMonthIndex + 1) % 12) shouldBe dateResult1.getMonth.toString

      val inputAtDec29th = localDateParam.withDayOfYear(363)
      val inputMonthsIndex1 = monthsList.indexOf(inputAtDec29th.getMonth.toString)
      val dateResult2 = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAtDec29th)
      monthsList((inputMonthsIndex1 + 1) % 12) shouldBe dateResult2.getMonth.toString

      val inputAtFeb14th = localDateParam.withDayOfYear(45)
      val inputMonthsIndex2 = monthsList.indexOf(inputAtFeb14th.getMonth.toString)
      val dateResult3 = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAtFeb14th)
      monthsList((inputMonthsIndex2) % 12) shouldBe dateResult3.getMonth.toString

    }

  }

}
