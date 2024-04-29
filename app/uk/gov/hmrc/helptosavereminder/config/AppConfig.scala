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

package uk.gov.hmrc.helptosavereminder.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (val config: Configuration, val servicesConfig: ServicesConfig) {

  val appName: String = config.get[String]("appName")

  val sendEmailTemplateId: String = config.get[String]("microservice.services.email.templateId")

  val nameParam: String = config.get[String]("microservice.services.email.nameParam")

  val monthParam: String = config.get[String]("microservice.services.email.monthParam")

  val userScheduleCronExpression: String = config.getOptional[String](s"userScheduleCronExpression").getOrElse("")

  val defaultRepoLockPeriod: Int = 120

  val repoLockPeriod: Int = config.getOptional[Int](s"mongodb.repoLockPeriod").getOrElse(defaultRepoLockPeriod)

  val defaultScheduleTake: Int = 500

  val scheduleTake: Int = config.getOptional[Int](s"scheduleTake").getOrElse(defaultScheduleTake)

  val excludedNinos: Seq[String] = config.getOptional[Seq[String]](s"excludedNinos").getOrElse(Seq())
}
