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

package uk.gov.hmrc.apiplatformoutboundsoap.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import org.apache.http.HttpStatus

import play.api.Logging
import play.api.http.HeaderNames.CONTENT_TYPE
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.SoapRequest

@Singleton
class OutboundConnector @Inject() (
    appConfig: AppConfig,
    httpClient: HttpClientV2
  )(implicit ec: ExecutionContext
  ) extends HttpErrorFunctions with Logging {

  val useProxy: Boolean = useProxyForEnv()

  def postMessage(messageId: String, soapRequest: SoapRequest): Future[Int] = {
    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(CONTENT_TYPE -> "application/soap+xml")

    def requestLogMessage(statusCode: Int) = s"Attempted request to ${soapRequest.destinationUrl} with message ID $messageId got status code $statusCode"

    postHttpRequest(soapRequest).map {
      case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
        logger.warn(requestLogMessage(statusCode))
        statusCode
      case Right(response: HttpResponse)                    =>
        response.status
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while requesting message with message ID $messageId", e)
          Future.successful(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      }
  }

  def postHttpRequest(soapRequest: SoapRequest)(implicit hc: HeaderCarrier) = {
    addProxy(httpClient.post(url"${soapRequest.destinationUrl}"))
      .withBody(soapRequest.soapEnvelope)
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  def useProxyForEnv(): Boolean = {
    appConfig.proxyRequiredForThisEnvironment
  }

  protected def addProxy(requestBuilder: RequestBuilder): RequestBuilder = if (useProxy) {
    requestBuilder.withProxy
  } else {
    requestBuilder
  }
}
