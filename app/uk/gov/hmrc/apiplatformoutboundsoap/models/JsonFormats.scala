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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig

import java.time.Instant

object JsonFormats {

  import uk.gov.hmrc.apiplatformoutboundsoap.GlobalContext.injector

  val appConfig: AppConfig                                    = injector.instanceOf[AppConfig]
  implicit val privateHeaderFormatter: OFormat[PrivateHeader] = Json.format[PrivateHeader]

  implicit val instantFormat: Format[Instant] = Format(Reads.DefaultInstantReads, Writes.InstantEpochMilliWrites)

  val addressingReads: Reads[Addressing]                                          = (
    (JsPath \ "from").read[String].orElse(Reads.pure(appConfig.addressingFrom)) and
      (JsPath \ "to").read[String] and
      (JsPath \ "replyTo").read[String].orElse(Reads.pure(appConfig.addressingReplyTo)) and
      (JsPath \ "faultTo").read[String].orElse(Reads.pure(appConfig.addressingFaultTo)) and
      (JsPath \ "messageId").read[String] and
      (JsPath \ "relatesTo").readNullable[String]
  )(Addressing.apply _)
  implicit val addressingFormatter: OFormat[Addressing]                           = OFormat(addressingReads, Json.writes[Addressing])
  implicit val soapMessageStatusFormatter: OFormat[SoapMessageStatus]             = Json.format[SoapMessageStatus]
  implicit val retryingSoapMessageFormatter: OFormat[RetryingOutboundSoapMessage] = Json.format[RetryingOutboundSoapMessage]
  implicit val failedSoapMessageFormatter: OFormat[FailedOutboundSoapMessage]     = Json.format[FailedOutboundSoapMessage]
  implicit val sentSoapMessageFormatter: OFormat[SentOutboundSoapMessage]         = Json.format[SentOutboundSoapMessage]
  implicit val codSoapMessageFormatter: OFormat[CodSoapMessage]                   = Json.format[CodSoapMessage]
  implicit val coeSoapMessageFormatter: OFormat[CoeSoapMessage]                   = Json.format[CoeSoapMessage]
  implicit val messageRequestFormatter: OFormat[MessageRequest]                   = Json.format[MessageRequest]

  implicit val outboundSoapMessageWrites: OWrites[OutboundSoapMessage]    = {
    case r @ RetryingOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _, _, _) =>
      retryingSoapMessageFormatter.writes(r) ++ Json.obj(
        "status" -> SendingStatus.RETRYING.entryName
      )
    case f @ FailedOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _, _)      =>
      failedSoapMessageFormatter.writes(f) ++ Json.obj(
        "status" -> SendingStatus.FAILED.entryName
      )
    case s @ SentOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _, _)        =>
      sentSoapMessageFormatter.writes(s) ++ Json.obj(
        "status" -> SendingStatus.SENT.entryName
      )
    case cod @ CodSoapMessage(_, _, _, _, _, _, _, _, _, _, _)               =>
      codSoapMessageFormatter.writes(cod) ++ Json.obj(
        "status" -> DeliveryStatus.COD.entryName
      )
    case coe @ CoeSoapMessage(_, _, _, _, _, _, _, _, _, _, _)               =>
      coeSoapMessageFormatter.writes(coe) ++ Json.obj(
        "status" -> DeliveryStatus.COE.entryName
      )
  }
  implicit val outboundSoapMessageFormatter: OFormat[OutboundSoapMessage] = OFormat(Json.reads[OutboundSoapMessage], outboundSoapMessageWrites)
}
