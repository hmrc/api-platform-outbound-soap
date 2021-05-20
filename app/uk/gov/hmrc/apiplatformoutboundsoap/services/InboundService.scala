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

package uk.gov.hmrc.apiplatformoutboundsoap.services

import akka.http.scaladsl.util.FastFuture.successful
import akka.stream.Materializer
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.NotificationCallbackConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.DeliveryStatus
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.xml.{NodeSeq, XML}

@Singleton
class InboundService @Inject()(outboundMessageRepository: OutboundMessageRepository,
                               notificationCallbackConnector: NotificationCallbackConnector,
                               appConfig: AppConfig)
                              (implicit val ec: ExecutionContext, mat: Materializer)
  extends HttpErrorFunctions {
  val logger: LoggerLike = Logger
  val dateTimeFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime()

  def now: DateTime = DateTime.now(UTC)

  def randomUUID: UUID = UUID.randomUUID

  def processConfirmation(confirmationRequest: String, confirmationType: Option[String])(implicit hc: HeaderCarrier) = {
    val deliveryStatus =  confirmationType.map(c => c.toUpperCase) match {
      case Some("COE") => DeliveryStatus.COE
      case Some("COD") => DeliveryStatus.COD
    }
    outboundMessageRepository.updateConfirmationStatus(findRelatesTo(confirmationRequest).text, deliveryStatus, confirmationRequest) map {
      foosm =>
        foosm.map(notificationCallbackConnector.sendNotification) map {
          _ =>()
          successful(None)
        }
    }
  }

  private def findRelatesTo(confirmationMessage: String): NodeSeq = {
    val message: scala.xml.Elem = XML.loadString(confirmationMessage)
    message \\ "RelatesTo"
  }
}
