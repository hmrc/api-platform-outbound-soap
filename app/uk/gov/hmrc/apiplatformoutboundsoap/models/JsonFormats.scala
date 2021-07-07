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

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._

object JsonFormats {
  val addressingReads: Reads[Addressing] = (
    (JsPath \ "from").readNullable[String] and
      (JsPath \ "to").read[String] and
      (JsPath \ "replyTo").read[String].orElse(Reads.pure("TBC")) and
      (JsPath \ "faultTo").readNullable[String] and
      (JsPath \ "messageId").read[String] and
      (JsPath \ "relatesTo").readNullable[String]
    ) (Addressing.apply _)

  implicit object DateTimeFormatter extends Format[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] =
      JodaReads.DefaultJodaDateTimeReads.reads(json)

    override def writes(dt: DateTime): JsValue =
      JodaWrites.JodaDateTimeWrites.writes(dt)
  }

  implicit val addressingFormatter: OFormat[Addressing] = OFormat(addressingReads, Json.writes[Addressing])
  implicit val soapMessageStatusFormatter: OFormat[SoapMessageStatus] = Json.format[SoapMessageStatus]
  implicit val retryingSoapMessageFormatter: OFormat[RetryingOutboundSoapMessage] = Json.format[RetryingOutboundSoapMessage]
  implicit val failedSoapMessageFormatter: OFormat[FailedOutboundSoapMessage] = Json.format[FailedOutboundSoapMessage]
  implicit val sentSoapMessageFormatter: OFormat[SentOutboundSoapMessage] = Json.format[SentOutboundSoapMessage]
  implicit val codSoapMessageFormatter: OFormat[CodSoapMessage] = Json.format[CodSoapMessage]
  implicit val coeSoapMessageFormatter: OFormat[CoeSoapMessage] = Json.format[CoeSoapMessage]

  val messageRequestReads: Reads[MessageRequest] = (
    (JsPath \ "wsdlUrl").read[String] and
      (JsPath \ "wsdlOperation").read[String] and
      (JsPath \ "messageBody").read[String] and
      (JsPath \ "addressing").read[Addressing] and
      ((JsPath \ "confirmationOfDelivery").read[Boolean] or Reads.pure(false)) and
      (JsPath \ "notificationUrl").readNullable[String]
    ) (MessageRequest.apply _)
  implicit val messageRequestFormatter: OFormat[MessageRequest] = OFormat(messageRequestReads, Json.writes[MessageRequest])

  implicit val outboundSoapMessageWrites: OWrites[OutboundSoapMessage] = new OWrites[OutboundSoapMessage] {
    override def writes(soapMessage: OutboundSoapMessage): JsObject = soapMessage match {
      case r @ RetryingOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _) =>
        retryingSoapMessageFormatter.writes(r) ++ Json.obj(
          "status" -> SendingStatus.RETRYING.entryName
        )
      case f @ FailedOutboundSoapMessage(_, _, _, _, _, _, _, _, _) =>
        failedSoapMessageFormatter.writes(f) ++ Json.obj(
          "status" -> SendingStatus.FAILED.entryName
        )
      case s @ SentOutboundSoapMessage(_, _, _, _, _, _, _, _, _) =>
        sentSoapMessageFormatter.writes(s) ++ Json.obj(
          "status" -> SendingStatus.SENT.entryName
        )
      case cod @ CodSoapMessage(_, _, _, _, _, _, _, _, _) =>
        codSoapMessageFormatter.writes(cod) ++ Json.obj(
          "status" -> DeliveryStatus.COD.entryName
        )
      case coe @ CoeSoapMessage(_, _, _, _, _, _, _, _, _) =>
        coeSoapMessageFormatter.writes(coe) ++ Json.obj(
          "status" -> DeliveryStatus.COE.entryName
        )
    }
  }
  implicit val outboundSoapMessageFormatter: OFormat[OutboundSoapMessage] = OFormat(Json.reads[OutboundSoapMessage], outboundSoapMessageWrites)
}
