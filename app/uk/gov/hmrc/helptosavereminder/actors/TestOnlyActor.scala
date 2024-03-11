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

import akka.actor.Actor
import akka.http.javadsl.model.DateTime
import akka.pattern.pipe
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._
import uk.gov.hmrc.helptosavereminder.models.{HtsUserSchedule, SendEmails, Stats}
import uk.gov.hmrc.helptosavereminder.repo.HtsReminderMongoRepository

import java.time.LocalDate
import scala.annotation.tailrec
import scala.collection.{immutable, mutable}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

class TestOnlyActor(repository: HtsReminderMongoRepository) extends Actor {
  private val emailsInFlight = mutable.HashSet[String]()
  private val emailsComplete = mutable.HashSet[String]()
  private val duplicates = mutable.HashSet[String]()
  private var dateStarted: Option[DateTime] = None
  private var dateFinished: Option[DateTime] = None
  private var dateAcknowledged: Option[DateTime] = None

  override def receive: Receive = {
    case CLEAR =>
      emailsInFlight.clear()
      emailsComplete.clear()
      duplicates.clear()
      dateStarted = Some(DateTime.now())
      dateFinished = None
      dateAcknowledged = None
    case Init(email) => emailsInFlight.add(email)
    case Acknowledge(email) =>
      if (emailsInFlight.remove(email)) {
        emailsComplete.add(email)
      } else {
        duplicates.add(email)
      }
      if (emailsInFlight.isEmpty) {
        dateAcknowledged = Some(DateTime.now())
      }
    case SUCCESS => dateFinished = Some(DateTime.now())
    case FAILURE => dateFinished = Some(DateTime.now())
    case GET_STATS =>
      sender ! Stats(
        emailsInFlight.toList,
        emailsComplete.toList,
        duplicates.toList,
        dateStarted.map(_.toString).orNull,
        dateFinished.map(_.toString).orNull,
        dateAcknowledged.map(_.toString).orNull
      )
    case SendEmails(emails) =>
      implicit val ec: ExecutionContext = context.dispatcher
      val prefixChars = (for (ch <- 'A' to 'Z') yield ch).toSet.diff("DFIQUVO".toSet).toList
      val postfixChars = (for (ch <- 'A' to 'D') yield ch).toList
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
        .pipeTo(sender)
  }
}
