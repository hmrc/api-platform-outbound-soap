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

package uk.gov.hmrc.apiplatformoutboundsoap.scheduled

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.services.OutboundService

@Singleton
class SoapMessageRetryJob @Inject() (appConfig: AppConfig, override val lockRepository: MongoLockRepository, outboundService: OutboundService)
    extends LockedScheduledJob {
  override val releaseLockAfter: Duration = appConfig.retryJobLockDuration

  override def name: String = "SoapMessageRetryJob"

  override def initialDelay: FiniteDuration = appConfig.retryInitialDelay.asInstanceOf[FiniteDuration]

  override def interval: FiniteDuration = appConfig.retryInterval.asInstanceOf[FiniteDuration]

  override def executeInLock(implicit ec: ExecutionContext): Future[Result] = {
    outboundService.retryMessages(HeaderCarrier()).map(done => Result(done.toString))
  }
}
