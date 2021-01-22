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

import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.messageResponseFormatter
import uk.gov.hmrc.apiplatformoutboundsoap.models.{OutboundSoapMessage, SoapMessageStatus}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationCallbackConnector @Inject()(httpClient: HttpClient)
                                             (implicit ec: ExecutionContext) extends HttpErrorFunctions {

  val logger: LoggerLike = Logger

  def sendNotification(message: OutboundSoapMessage)(implicit hc: HeaderCarrier): Future[Option[Int]] = {
    (message.notificationUrl map { url =>
      httpClient.POST[SoapMessageStatus, HttpResponse](url, SoapMessageStatus(message.globalId, message.messageId, message.status)) map { response =>
        if (!is2xx(response.status)) {
          logger.warn(s"Attempted request to $url responded with HTTP response code ${response.status}")
        }
        Some(response.status)
      }
    }).getOrElse(successful(None))
  }
}
