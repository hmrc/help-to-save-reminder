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

package uk.gov.hmrc.helptosavereminder.config

import akka.actor.{ActorSystem, Props}
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.helptosavereminder.actors.ProcessingSupervisor
import uk.gov.hmrc.helptosavereminder.models.ActorUtils._

import scala.concurrent.ExecutionContext

@Singleton
class Scheduler @Inject()(
  httpClient: HttpClient,
  actorSystem: ActorSystem,
  env: Environment,
  mongoApi: play.modules.reactivemongo.ReactiveMongoComponent,
  config: Configuration,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {

  lazy val reminderSupervisor = actorSystem.actorOf(
    Props(classOf[ProcessingSupervisor], mongoApi, config, httpClient, env, servicesConfig, ec),
    "reminder-supervisor"
  )

  Logger.debug("About to send a BootStrap message to the Supervisor")

  reminderSupervisor ! BOOTSTRAP

}
