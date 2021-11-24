/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavereminder.models.test

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.UUID

import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.helptosavereminder.models.HtsUserSchedule
import uk.gov.hmrc.helptosavereminder.util.DateTimeFunctions.getNextSendDate

import scala.util.Random

object ReminderGenerator {
  private lazy val rand = new Random()
  private lazy val generator = new Generator(rand)

  private def nino: Nino = generator.nextNino
  private def email(prefix: String) = s"$prefix+${UUID.randomUUID()}@digital.hmrc.gov.uk"
  private def email = s"$firstName.$lastName+${UUID.randomUUID()}@digital.hmrc.gov.uk"
  private def firstName: String = "Mohan"
  private def lastName: String = "Dolla"
  private def daysToReceive = Seq(1, 25) //scalastyle:ignore magic.number
  private def nextSendDate: LocalDate =
    getNextSendDate(Seq(1, 25), LocalDate.now(ZoneId.of("Europe/London"))) //scalastyle:ignore magic.number
      .getOrElse(LocalDate.now(ZoneId.of("Europe/London")))
  private def callBackUrlRef: String = LocalDateTime.now().toString + nino.value
  private def accountClosingDate: Option[LocalDate] =
    Some(LocalDate.now(ZoneId.of("Europe/London")).plusMonths(6)) //scalastyle:ignore magic.number

  def nextReminder(emailPrefix: String, daysToReceive: Seq[Int]): HtsUserSchedule =
    HtsUserSchedule(
      nino,
      email(emailPrefix),
      firstName,
      lastName,
      true,
      daysToReceive,
      nextSendDate,
      callBackUrlRef,
      accountClosingDate
    )

  def nextReminder: HtsUserSchedule =
    HtsUserSchedule(
      nino,
      email,
      firstName,
      lastName,
      true,
      daysToReceive,
      nextSendDate,
      callBackUrlRef,
      accountClosingDate
    )

}
