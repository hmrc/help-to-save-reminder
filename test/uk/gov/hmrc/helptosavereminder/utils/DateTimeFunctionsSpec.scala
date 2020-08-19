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

import java.util.Calendar

import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions

class DateTimeFunctionsSpec extends WordSpec with Matchers with GuiceOneAppPerSuite {

  val lastDayOftheMonth = Calendar.getInstance.getActualMaximum(Calendar.DAY_OF_MONTH);

  "DateTimeFunctions object " should {
    "return appropriate day " in {
      val returnDate1 = DateTimeFunctions.getNextSendDate(Seq(1))
      val returnDate2 = DateTimeFunctions.getNextSendDate(Seq(10))
      val returnDate3 = DateTimeFunctions.getNextSendDate(Seq(1, 10))
      val returnDate4 = DateTimeFunctions.getNextSendDate(Seq(25))
      val returnDate5 = DateTimeFunctions.getNextSendDate(Seq(27, 28))

      val returnDate6 = DateTimeFunctions.getNextSendDate(Seq(15, 33))

    }

  }

}
