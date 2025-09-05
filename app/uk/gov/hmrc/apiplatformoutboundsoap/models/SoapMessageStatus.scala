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

package uk.gov.hmrc.apiplatformoutboundsoap.models

import java.time.Instant
import java.util.UUID

case class SoapMessageStatus(
    globalId: UUID,
    messageId: String,
    status: StatusType,
    ccnHttpStatus: Option[Int],
    sentDateTime: Option[Instant],
    privateHeaders: Option[List[PrivateHeader]]
  )

object SoapMessageStatus {

  def fromOutboundSoapMessage(outboundSoapMessage: OutboundSoapMessage): SoapMessageStatus = {
    SoapMessageStatus(
      outboundSoapMessage.globalId,
      outboundSoapMessage.messageId,
      outboundSoapMessage.status,
      outboundSoapMessage.ccnHttpStatus match {
        case Some(s) => Some(s)
        case None    => Some(0)
      },
      outboundSoapMessage.sentDateTime,
      outboundSoapMessage.privateHeaders
    )
  }
}
