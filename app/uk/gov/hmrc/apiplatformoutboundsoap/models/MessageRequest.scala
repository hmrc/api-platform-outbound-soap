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

import uk.gov.hmrc.apiplatformoutboundsoap.utils.Require.validate

case class MessageRequest(
    wsdlUrl: String,
    wsdlOperation: String,
    messageBody: String,
    addressing: Addressing,
    confirmationOfDelivery: Option[Boolean],
    notificationUrl: Option[String] = None,
    privateHeaders: Option[List[PrivateHeader]] = None
  )

case class Addressing(from: String, to: String, replyTo: String, faultTo: String, messageId: String, relatesTo: Option[String] = None) {
  validate(to.trim != "", "addressing.to being empty")
  validate(messageId.trim != "", "addressing.messageId being empty")
  validate(from.trim != "", "addressing.from being empty")
}

case class PrivateHeader(name: String, value: String) {
  validate(name.trim.length <= 1024, "privateHeaders name is longer than 1024 characters")
  validate(value.trim.length <= 1024, "privateHeaders value is longer than 1024 characters")
}
