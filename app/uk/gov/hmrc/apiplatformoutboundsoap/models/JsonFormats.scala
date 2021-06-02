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
  implicit val addressingFormatter: OFormat[Addressing] = Json.format[Addressing]
  implicit val confirmationFormatter: OFormat[Confirmation] = Json.format[Confirmation]
  implicit val soapMessageStatusFormatter: OFormat[SoapMessageStatus] = Json.format[SoapMessageStatus]

  val messageRequestReads: Reads[MessageRequest] = (
    (JsPath \ "wsdlUrl").read[String] and
    (JsPath \ "wsdlOperation").read[String] and
    (JsPath \ "messageBody").read[String] and
    (JsPath \ "addressing").read[Addressing] and
    ((JsPath \ "confirmationOfDelivery").read[Boolean] or Reads.pure(false)) and
    (JsPath \ "notificationUrl").readNullable[String]
    )(MessageRequest.apply _)
  implicit val messageRequestFormatter: OFormat[MessageRequest] = OFormat(messageRequestReads, Json.writes[MessageRequest])
}
