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

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

trait LockedScheduledJob extends ScheduledJob {
  def name: String

  def initialDelay: FiniteDuration

  def interval: FiniteDuration

  def enabled: Boolean

  def executeInLock(implicit ec: ExecutionContext): Future[Result]

  val mongoLockRepository: MongoLockRepository

  // Lock for 10 minutes longer than the interval to allow for retries and timeouts
  lazy val lockService: LockService = LockService(mongoLockRepository, lockId = s"$name-lock", ttl = interval + 10.minutes)

  final def execute(implicit ec: ExecutionContext): Future[Result] =
    if (enabled) {
      lockService.withLock {
        executeInLock
      } map {
        case Some(Result(message)) => Result(s"Job named $name ran and completed with result $message")
        case None                  => Result(s"Job named $name cannot acquire Mongo lock, not running")
      }
    } else {
      Future.successful(Result(s"Job named $name is disabled"))
    }

  override def toString = s"$name after $initialDelay every $interval"
}
