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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.soapMessageStatusFormatter
import uk.gov.hmrc.apiplatformoutboundsoap.models.{OutboundSoapMessage, SoapMessageStatus}

@Singleton
class NotificationCallbackConnector @Inject() (httpClient: HttpClientV2)(implicit ec: ExecutionContext) extends HttpErrorFunctions with Logging {

  def sendNotification(message: OutboundSoapMessage)(implicit hc: HeaderCarrier): Future[Option[Int]] = {
    (message.notificationUrl map { url =>
      httpClient.post(url"${url}")
        .withBody(Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(message)))
        .execute[HttpResponse] map { response =>
        logger.info(s"Notification to $url with global ID ${message.globalId} and message ID ${message.messageId} responded with HTTP code ${response.status}")
        Some(response.status)
      } recover {
        case e: Exception =>
          logger.error(s"Notification to $url with global ID ${message.globalId} and message ID ${message.messageId} failed: ${e.getMessage}", e)
          None
      }
    }).getOrElse(successful(None))
  }
}
