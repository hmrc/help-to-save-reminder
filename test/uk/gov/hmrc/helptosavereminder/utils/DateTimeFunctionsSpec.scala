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

  val localDateParam = LocalDate.now(ZoneId.of("Europe/London"))

  "DateTimeFunctions object " should {

    val localDateParam = LocalDate.now(ZoneId.of("Europe/London"))
    val startOfMonth = localDateParam.withDayOfMonth(1)
    val nextMonthFirstDay = startOfMonth.plusMonths(1)

    "return correct nextSendDate for any day in the current month of the year" in {

      //We are now at the first day of the present month
      val inputAt1stDayOfPresentMonth = localDateParam.withDayOfMonth(1)
      val inputMonthsIndex = monthsList.indexOf(inputAt1stDayOfPresentMonth.getMonth.toString)

      val dateResult = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAt1stDayOfPresentMonth)
      monthsList((inputMonthsIndex) % 12) shouldBe dateResult.getMonth.toString
      dateResult.getDayOfMonth shouldBe 25

      val dateResult1 = DateTimeFunctions.getNextSendDate(Seq(1), inputAt1stDayOfPresentMonth)
      monthsList((inputMonthsIndex + 1) % 12) shouldBe dateResult1.getMonth.toString
      dateResult1.getDayOfMonth shouldBe 1

      val dateResult2 = DateTimeFunctions.getNextSendDate(Seq(25), inputAt1stDayOfPresentMonth)
      monthsList((inputMonthsIndex) % 12) shouldBe dateResult2.getMonth.toString
      dateResult2.getDayOfMonth shouldBe 25

      //Now we are at the 10th day of the present month and repeat the previous three tests.
      val inputAt10thtDayOfPresentMonth = localDateParam.withDayOfMonth(10)
      val dateResult3 = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAt10thtDayOfPresentMonth)
      monthsList((inputMonthsIndex) % 12) shouldBe dateResult3.getMonth.toString
      dateResult3.getDayOfMonth shouldBe 25

      val dateResult4 = DateTimeFunctions.getNextSendDate(Seq(1), inputAt10thtDayOfPresentMonth)
      monthsList((inputMonthsIndex + 1) % 12) shouldBe dateResult4.getMonth.toString
      dateResult4.getDayOfMonth shouldBe 1

      val dateResult5 = DateTimeFunctions.getNextSendDate(Seq(25), inputAt10thtDayOfPresentMonth)
      monthsList((inputMonthsIndex) % 12) shouldBe dateResult5.getMonth.toString
      dateResult5.getDayOfMonth shouldBe 25

      //Now we are at the 25th day of the present month and repeat the previous three tests.
      val inputAt25thtDayOfPresentMonth = localDateParam.withDayOfMonth(25)
      val dateResult6 = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAt25thtDayOfPresentMonth)
      monthsList((inputMonthsIndex + 1) % 12) shouldBe dateResult6.getMonth.toString
      dateResult6.getDayOfMonth shouldBe 1

      val dateResult7 = DateTimeFunctions.getNextSendDate(Seq(1), inputAt25thtDayOfPresentMonth)
      monthsList((inputMonthsIndex + 1) % 12) shouldBe dateResult7.getMonth.toString
      dateResult7.getDayOfMonth shouldBe 1

      val dateResult8 = DateTimeFunctions.getNextSendDate(Seq(25), inputAt25thtDayOfPresentMonth)
      monthsList((inputMonthsIndex + 1) % 12) shouldBe dateResult8.getMonth.toString
      dateResult8.getDayOfMonth shouldBe 25

    }

    "return correct nextSendDate for a day in February of the year" in {

      val inputAtFeb14th = localDateParam.withDayOfYear(45)
      val inputMonthsIndex = monthsList.indexOf(inputAtFeb14th.getMonth.toString)
      val dateResult = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAtFeb14th)
      monthsList((inputMonthsIndex) % 12) shouldBe dateResult.getMonth.toString
      dateResult.getDayOfMonth shouldBe 25

    }

    "return correct nextSendDate for a day in the December month of the year" in {

      val inputAtDec29th = localDateParam.withDayOfYear(363)
      val inputMonthsIndex = monthsList.indexOf(inputAtDec29th.getMonth.toString)
      val dateResult: LocalDate = DateTimeFunctions.getNextSendDate(Seq(1, 25), inputAtDec29th)
      monthsList((inputMonthsIndex + 1) % 12) shouldBe dateResult.getMonth.toString
      dateResult.getDayOfMonth shouldBe 1

    }

  }

}
