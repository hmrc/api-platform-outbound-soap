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

import org.joda.time.Duration
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.services.OutboundService
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.scheduling.LockedScheduledJob

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SoapMessageRetryJob @Inject()(appConfig: AppConfig, override val lockRepository: LockRepository,
                                    outboundService: OutboundService)
  extends LockedScheduledJob {
  override val releaseLockAfter: Duration = Duration.standardSeconds(appConfig.retryJobLockDuration.toSeconds)

  override def name: String = "SoapMessageRetryJob"

  override def initialDelay: FiniteDuration = appConfig.retryInitialDelay.asInstanceOf[FiniteDuration]

  override def interval: FiniteDuration = appConfig.retryInterval.asInstanceOf[FiniteDuration] / 10

  override def executeInLock(implicit ec: ExecutionContext): Future[Result] = {
    outboundService.retryMessages.map(done => Result(done.toString))
  }
}