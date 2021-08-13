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

package uk.gov.hmrc.apiplatformoutboundsoap.scheduled

import com.google.inject.AbstractModule
import javax.inject.{Inject, Provider, Singleton}
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import play.api.{Application, Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.lock.LockRepository

import scala.concurrent.ExecutionContext

class SchedulerModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Scheduler]).asEagerSingleton()
  }
}

@Singleton
class Scheduler @Inject()(override val applicationLifecycle: ApplicationLifecycle,
                          override val application: Application,
                          soapMessageRetryJob: SoapMessageRetryJob,
                          appConfig: AppConfig)
                         (override implicit val ec: ExecutionContext) extends RunningOfScheduledJobs {
  override lazy val scheduledJobs: Seq[LockedScheduledJob] = if (appConfig.retryEnabled) Seq(soapMessageRetryJob) else Seq()
}

class SchedulerPlayModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[LockRepository].toProvider[LockRepositoryProvider]
    )
  }
}

@Singleton
class LockRepositoryProvider @Inject()(mongoComponent: ReactiveMongoComponent) extends Provider[LockRepository] {
  override def get(): LockRepository = new LockRepository()(mongoComponent.mongoConnector.db)
}
