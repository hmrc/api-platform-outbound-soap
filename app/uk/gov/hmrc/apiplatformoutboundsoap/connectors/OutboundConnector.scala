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

package uk.gov.hmrc.apiplatformoutboundsoap.connectors

import javax.inject.{Inject, Singleton}
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundConnector @Inject()(appConfig: AppConfig, httpClient: HttpClient)(implicit ec: ExecutionContext) {

  val logger: LoggerLike = Logger

  def postMessage(message: String): Future[Int] = {
    implicit val hc: HeaderCarrier =  HeaderCarrier().withExtraHeaders(CONTENT_TYPE -> "application/soap+xml")

    httpClient.POSTString[HttpResponse](appConfig.ccn2Url, message) map { response =>
      if (response.status >= 400) {
        logger.warn(s"Attempted request to ${appConfig.ccn2Url} responded with HTTP response code ${response.status}")
      }
      response.status
    }
  }
}
