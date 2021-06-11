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

package uk.gov.hmrc.apiplatformoutboundsoap.connectors

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.{Configuration, Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.models.SoapRequest
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundConnector @Inject()(
               config: Configuration,
               defaultHttpClient: HttpClient,
              proxiedHttpClient: ProxiedHttpClient)
             (implicit ec: ExecutionContext) extends HttpErrorFunctions {

  val logger: LoggerLike = Logger
  val useProxy: Boolean = useProxyForEnv()
  lazy val httpClient: HttpClient = if (useProxy) proxiedHttpClient else defaultHttpClient

  def postMessage(soapRequest: SoapRequest): Future[Int] = {
    implicit val hc: HeaderCarrier =  HeaderCarrier().withExtraHeaders(CONTENT_TYPE -> "application/soap+xml")

    httpClient.POSTString[HttpResponse](soapRequest.destinationUrl, soapRequest.soapEnvelope) map { response =>
      if (!is2xx(response.status)) {
        logger.warn(s"Attempted request to ${soapRequest.destinationUrl} responded with HTTP response code ${response.status}")
      }
      response.status
    }
  }


  def useProxyForEnv(): Boolean = {
    config.getOptional[Boolean]("proxy.proxyRequiredForThisEnvironment").getOrElse(false)
  }
}
