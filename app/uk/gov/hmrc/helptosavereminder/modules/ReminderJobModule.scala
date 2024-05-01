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

package uk.gov.hmrc.helptosavereminder.modules

import com.google.inject.AbstractModule
import play.api.libs.concurrent.PekkoGuiceSupport
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavereminder.config.{Diagnostics, EmailDeleter, Scheduler}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector

class ReminderJobModule extends AbstractModule with PekkoGuiceSupport {
  override def configure(): Unit = {
    bind(classOf[Scheduler]).asEagerSingleton()
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector]).asEagerSingleton()
    bind(classOf[EmailDeleter]).asEagerSingleton()
    bind(classOf[Diagnostics]).asEagerSingleton()
  }
}
