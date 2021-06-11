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

import uk.gov.hmrc.apiplatformoutboundsoap.connectors.NotificationCallbackConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.models.common.{MessageIdNotFoundResult, NoContentUpdateResult, UpdateResult}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

@Singleton
class ConfirmationService @Inject()(outboundMessageRepository: OutboundMessageRepository,
                                    notificationCallbackConnector: NotificationCallbackConnector)
                                   (implicit val ec: ExecutionContext) {
 def processConfirmation(confRqst: NodeSeq, msgId: String, delStatus: DeliveryStatus)(implicit hc: HeaderCarrier): Future[UpdateResult] = {


    def doUpdate(id: String, status: DeliveryStatus, body: String): Future[NoContentUpdateResult.type] = {
      outboundMessageRepository.updateConfirmationStatus(id, status, body) map { maybeOutboundSoapMessage =>
        maybeOutboundSoapMessage.map(outboundSoapMessage => notificationCallbackConnector.sendNotification(outboundSoapMessage))} map {
        case _ => NoContentUpdateResult
      }
    }

    outboundMessageRepository.findById(msgId).flatMap {
      case None => Future.successful(MessageIdNotFoundResult)
      case Some(_: OutboundSoapMessage) => doUpdate(msgId, delStatus, confRqst.text)
    }
 }
}
