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

package uk.gov.hmrc.apiplatformoutboundsoap.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration

@Singleton
class AppConfig @Inject()(config: Configuration, servicesConfig: ServicesConfig) {

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String = config.get[String]("microservice.metrics.graphite.host")

  val ccn2Url: String = config.get[String]("ccn2Url")
  val ccn2Username: String = config.get[String]("ccn2Username")
  val ccn2Password: String = config.get[String]("ccn2Password")

  val retryInterval: Duration = Duration(config.getOptional[String]("retry.interval").getOrElse("60 sec"))
  val retryDuration: Duration = Duration(config.getOptional[String]("retry.duration").getOrElse("5 min"))
  val retryInitialDelay: Duration = Duration(config.getOptional[String]("retry.initial.delay").getOrElse("30 sec"))
  val retryEnabled: Boolean = config.getOptional[Boolean]("retry.enabled").getOrElse(false)
  val retryJobLockDuration: Duration = Duration(config.getOptional[String]("retry.lock.duration").getOrElse("1 hr"))
  val retryMessagesTtl: Duration = Duration(config.getOptional[String]("retry.messages.ttl").getOrElse("30 day"))
  val parallelism: Int = config.getOptional[Int]("retry.parallelism").getOrElse(5)
}
