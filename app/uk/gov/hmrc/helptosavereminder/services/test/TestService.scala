/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavereminder.services.test

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.models.HtsUserSchedule

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.helptosavereminder.models.test.ReminderGenerator
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderRepository

import java.time.LocalDate
import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

@Singleton
class TestService @Inject() (htsReminderRepository: HtsReminderRepository) {

  def generateAndInsertReminder(emailPrefix: String, daysToReceive: Seq[Int]): Future[Boolean] =
    htsReminderRepository.updateReminderUser(ReminderGenerator.nextReminder(emailPrefix, daysToReceive))

  def generateRecipients(emails: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    val prefixChars = ('A' to 'Z').toSet.diff("DFIQUVO".toSet).toList
    val postfixChars = ('A' to 'D').toList
    val random = new Random()
    emails
      .map(email => {
        @tailrec
        def generateNino(): String = {
          val prefix =
            s"${prefixChars(random.nextInt(prefixChars.length))}${prefixChars(random.nextInt(prefixChars.length))}"
          val postfix = postfixChars(random.nextInt(postfixChars.length))
          val digits = (for (_ <- 1 to 6) yield random.nextInt(10)).toList.mkString("")
          val nino = s"$prefix$digits$postfix"
          if (Nino.isValid(nino)) nino else generateNino()
        }
        val nino = generateNino()
        for {
          _ <- htsReminderRepository.updateReminderUser(
                HtsUserSchedule(
                  nino = Nino(nino),
                  email = email,
                  firstName = "First name",
                  lastName = "Last name",
                  optInStatus = true,
                  daysToReceive = immutable.Seq(1),
                  nextSendDate = LocalDate.now(),
                  callBackUrlRef = nino,
                  endDate = None
                )
              )
          _ <- htsReminderRepository.updateNextSendDate(nino, LocalDate.now().minusDays(1))
        } yield ()
      })
      .pipe(Future.sequence(_))
      .map(_ => ())
  }
}
