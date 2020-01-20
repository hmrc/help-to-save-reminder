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

package uk.gov.hmrc.helptosavereminder.models.test

import java.time.LocalDate
import java.util.UUID

import uk.gov.hmrc.auth.core.retrieve.Email
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.helptosavereminder.models.Reminder

import scala.util.Random

object ReminderGenerator {
  private lazy val rand = new Random()
  private lazy val generator = new Generator(rand)

  private def nino: Nino = generator.nextNino
  private def email: Email = Email(s"${UUID.randomUUID()}@test.com")
  private def name: String = "Test Name"
  private def daysToReceive = Seq(1, 25)
  private def nextSendDate: LocalDate = LocalDate.parse("2020-01-01")

  def nextReminder: Reminder = Reminder(nino, email, name, true, daysToReceive, nextSendDate)
}
