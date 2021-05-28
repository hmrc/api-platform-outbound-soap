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

import akka.stream.Materializer
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.NotificationCallbackConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.{DeliveryStatus, OutboundSoapMessage}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{NodeSeq, XML}

@Singleton
class ConfirmationService @Inject()(outboundMessageRepository: OutboundMessageRepository,
                                    notificationCallbackConnector: NotificationCallbackConnector)
                                   (implicit val ec: ExecutionContext, mat: Materializer)
  extends HttpErrorFunctions {
  val logger: LoggerLike = Logger
  val dateTimeFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime()


def processConfirmation(confirmationRequest: Option[String], deliveryStatus: DeliveryStatus)(implicit hc: HeaderCarrier) = {
    val node = findRelatesTo(confirmationRequest)

    if (node.nonEmpty) {
      confirmationRequest.map(cr => outboundMessageRepository.updateConfirmationStatus(node.get.text, deliveryStatus, cr)
        map { foo => foo.map(notificationCallbackConnector.sendNotification) })
      ()
    } else {
      logger.warn("RelatesTo not found so confirmation could not be processed")
      //TODO return something to controller to enable it to return 400 to caller
    }
  }

  private def findRelatesTo(confirmationMessage: Option[String]): Option[NodeSeq] = {
    confirmationMessage.map(cf => XML.loadString(cf)).map(rt => rt \\ "RelatesTo")
  }
}
