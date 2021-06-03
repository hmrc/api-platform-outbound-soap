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

package uk.gov.hmrc.apiplatformoutboundsoap.models

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, OFormat, Reads}

object JsonFormats {
  val addressingReads: Reads[Addressing] = (
    (JsPath \ "from").readNullable[String] and
    (JsPath \ "to").read[String] and
    (JsPath \ "replyTo").readNullable[String].orElse(Reads.pure(Some("TBC"))) and
    (JsPath \ "faultTo").readNullable[String] and
    (JsPath \ "messageId").read[String] and
    (JsPath \ "relatesTo").readNullable[String]
  ) (Addressing.apply _)
  implicit val addressingFormatter: OFormat[Addressing] = OFormat(addressingReads, Json.writes[Addressing])
  implicit val soapMessageStatusFormatter: OFormat[SoapMessageStatus] = Json.format[SoapMessageStatus]

  val messageRequestReads: Reads[MessageRequest] = (
    (JsPath \ "wsdlUrl").read[String] and
    (JsPath \ "wsdlOperation").read[String] and
    (JsPath \ "messageBody").read[String] and
    (JsPath \ "addressing").read[Addressing] and
    ((JsPath \ "confirmationOfDelivery").read[Boolean] or Reads.pure(false)) and
    (JsPath \ "notificationUrl").readNullable[String]
  ) (MessageRequest.apply _)
  implicit val messageRequestFormatter: OFormat[MessageRequest] = OFormat(messageRequestReads, Json.writes[MessageRequest])
}
