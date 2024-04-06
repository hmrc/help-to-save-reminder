/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavereminder.actors

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.models.{HtsUserSchedule, Stats}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository

import java.time.{LocalDate, LocalDateTime}
import scala.annotation.tailrec
import scala.collection.{immutable, mutable}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

trait StatCollector {
  def clear(): Unit
  def init(email: String): Unit
  def acknowledge(email: String): Unit
  def signalSuccess(): Unit
  def signalFailure(): Unit
  def stats: Stats
  def generateRecipients(emails: List[String])(implicit ec: ExecutionContext): Future[Unit]
}

class TestOnlyActor(repository: HtsReminderMongoRepository) extends StatCollector {
  private val emailsInFlight = mutable.HashSet[String]()
  private val emailsComplete = mutable.HashSet[String]()
  private val duplicates = mutable.HashSet[String]()
  private var dateStarted: Option[LocalDateTime] = None
  private var dateFinished: Option[LocalDateTime] = None
  private var dateAcknowledged: Option[LocalDateTime] = None

  def clear(): Unit = {
    emailsInFlight.clear()
    emailsComplete.clear()
    duplicates.clear()
    dateStarted = Some(LocalDateTime.now())
    dateFinished = None
    dateAcknowledged = None
  }

  def init(email: String): Unit = emailsInFlight.add(email)
  def acknowledge(email: String): Unit = {
    if (emailsInFlight.remove(email)) {
      emailsComplete.add(email)
    } else {
      duplicates.add(email)
    }
    if (emailsInFlight.isEmpty) {
      dateAcknowledged = Some(LocalDateTime.now())
    }
  }
  def signalSuccess(): Unit = dateFinished = Some(LocalDateTime.now())
  def signalFailure(): Unit = dateFinished = Some(LocalDateTime.now())
  def stats: Stats =
    Stats(
      emailsInFlight.toList,
      emailsComplete.toList,
      duplicates.toList,
      dateStarted.toString,
      dateFinished.map(_.toString).orNull,
      dateAcknowledged.map(_.toString).orNull
    )

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
          _ <- repository.updateReminderUser(
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
          _ <- repository.updateNextSendDate(nino, LocalDate.now().minusDays(1))
        } yield ()
      })
      .pipe(Future.sequence(_))
      .map(_ => ())
  }
}
