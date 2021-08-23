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

import akka.actor.ActorSystem
import org.apache.http.HttpStatus
import play.api.Logging
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.ws.WSClient
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.http.TwoWaySSLProxyClient
import uk.gov.hmrc.apiplatformoutboundsoap.models.SoapRequest
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.HttpAuditing

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class OutboundConnector @Inject()(
                                   httpAuditing: HttpAuditing,
                                   appConfig: AppConfig,
                                   wsClient: WSClient,
                                   defaultHttpClient: HttpClient,
                                   proxiedHttpClient: ProxiedHttpClient,
                                   system: ActorSystem
                                   )
                                 (implicit ec: ExecutionContext) extends HttpErrorFunctions with Logging {

  val proxyClient: HttpClient = new TwoWaySSLProxyClient(
    httpAuditing,
    appConfig.config,
    wsClient,
    "microservice.services.auth",
    system)
  case class UseProxyOrTwoWAYSSL(useProxy: Boolean, useTwoWaySSL: Boolean)

  lazy val httpClient: HttpClient =
    if (useProxyOrTwoWAYSSL.useProxy) proxiedHttpClient
    else if(useProxyOrTwoWAYSSL.useTwoWaySSL) proxyClient
    else defaultHttpClient

  val useProxyOrTwoWAYSSL = useProxyOrTwoWaySSLOrNot()

  def postMessage(messageId: String, soapRequest: SoapRequest): Future[Int] = {
    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(CONTENT_TYPE -> "application/soap+xml")

    def requestLogMessage(statusCode: Int) = s"Attempted request to ${soapRequest.destinationUrl} with message ID $messageId got status code $statusCode"

    postHttpRequest(soapRequest).map {
      case Left(UpstreamErrorResponse(_, statusCode, _, _)) =>
        logger.warn(requestLogMessage(statusCode))
        statusCode
      case Right(response: HttpResponse) =>
        response.status
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.warn(s"NonFatal error ${e.getMessage} while requesting message with message ID $messageId", e)
          Future.successful(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      }
  }

  def postHttpRequest(soapRequest: SoapRequest)(implicit hc: HeaderCarrier) = {
    httpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](soapRequest.destinationUrl, soapRequest.soapEnvelope)
  }

  def useProxyOrTwoWaySSLOrNot(): UseProxyOrTwoWAYSSL = {
    UseProxyOrTwoWAYSSL(appConfig.proxyRequiredForThisEnvironment, appConfig.twoWaySSLRequiredForThisEnvironment)
  }
}
