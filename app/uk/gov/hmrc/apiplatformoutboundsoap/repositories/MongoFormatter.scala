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

package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import uk.gov.hmrc.apiplatformoutboundsoap.models._

private[repositories] object MongoFormatter extends MongoJavatimeFormats.Implicits {

  implicit val cfg: Aux[Json.MacroOptions]                 = JsonConfiguration(
    discriminator = "status",
    typeNaming = JsonNaming { fullName =>
      OutboundSoapMessage.typeToStatus(fullName).toString()
    }
  )
  implicit val privateHeaderReads: Reads[PrivateHeader]    = Json.reads[PrivateHeader]
  implicit val privateHeaderWrites: OWrites[PrivateHeader] = Json.writes[PrivateHeader]

  implicit val privateHeaderFormatter: OFormat[PrivateHeader] =
    OFormat(privateHeaderReads, privateHeaderWrites)

  implicit val retryingMessageReads: Reads[RetryingOutboundSoapMessage] =
    Json.reads[RetryingOutboundSoapMessage]

  implicit val retryingMessageWrites: OWrites[RetryingOutboundSoapMessage] =
    Json.writes[RetryingOutboundSoapMessage].transform(_ ++ Json.obj("status" -> SendingStatus.RETRYING.toString()))

  implicit val retryingSoapMessageFormatter: OFormat[RetryingOutboundSoapMessage] =
    OFormat(retryingMessageReads, retryingMessageWrites)

  implicit val sentMessageReads: Reads[SentOutboundSoapMessage] =
    Json.reads[SentOutboundSoapMessage]

  implicit val sentMessageWrites: OWrites[SentOutboundSoapMessage] =
    Json.writes[SentOutboundSoapMessage].transform(_ ++ Json.obj("status" -> SendingStatus.SENT.toString()))

  implicit val sentSoapMessageFormatter: OFormat[SentOutboundSoapMessage] =
    OFormat(sentMessageReads, sentMessageWrites)

  implicit val failedMessageReads: Reads[FailedOutboundSoapMessage] =
    Json.reads[FailedOutboundSoapMessage]

  implicit val failedMessageWrites: OWrites[FailedOutboundSoapMessage] =
    Json.writes[FailedOutboundSoapMessage].transform(_ ++ Json.obj("status" -> SendingStatus.FAILED.toString()))

  implicit val failedSoapMessageFormatter: OFormat[FailedOutboundSoapMessage] =
    OFormat(failedMessageReads, failedMessageWrites)

  implicit val codMessageReads: Reads[CodSoapMessage] =
    Json.reads[CodSoapMessage]

  implicit val codMessageWrites: OWrites[CodSoapMessage] =
    Json.writes[CodSoapMessage].transform(_ ++ Json.obj("status" -> DeliveryStatus.COD.toString()))

  implicit val codSoapMessageFormatter: OFormat[CodSoapMessage] =
    OFormat(codMessageReads, codMessageWrites)

  implicit val coeMessageReads: Reads[CoeSoapMessage] =
    Json.reads[CoeSoapMessage]

  implicit val coeMessageWrites: OWrites[CoeSoapMessage] =
    Json.writes[CoeSoapMessage].transform(_ ++ Json.obj("status" -> DeliveryStatus.COE.toString()))

  implicit val coeSoapMessageFormatter: OFormat[CoeSoapMessage] =
    OFormat(coeMessageReads, coeMessageWrites)

  implicit val outboundSoapMessageReads: Reads[OutboundSoapMessage] =
    (JsPath \ "status").read[String].flatMap {
      case "RETRYING" =>
        retryingSoapMessageFormatter.widen[OutboundSoapMessage]
      case "SENT"     =>
        sentSoapMessageFormatter.widen[OutboundSoapMessage]
      case "FAILED"   =>
        failedSoapMessageFormatter.widen[OutboundSoapMessage]
      case "COD"      =>
        codSoapMessageFormatter.widen[OutboundSoapMessage]
      case "COE"      =>
        coeSoapMessageFormatter.widen[OutboundSoapMessage]
    }

  implicit val outboundSoapMessageWrites: OWrites[OutboundSoapMessage] = new OWrites[OutboundSoapMessage] {

    override def writes(soapMessage: OutboundSoapMessage): JsObject = soapMessage match {
      case r @ RetryingOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _, _, _) =>
        retryingSoapMessageFormatter.writes(r) ++ Json.obj(
          "status" -> SendingStatus.RETRYING.toString()
        )
      case f @ FailedOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _, _)      =>
        failedSoapMessageFormatter.writes(f) ++ Json.obj(
          "status" -> SendingStatus.FAILED.toString()
        )
      case s @ SentOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _, _)        =>
        sentSoapMessageFormatter.writes(s) ++ Json.obj(
          "status" -> SendingStatus.SENT.toString()
        )
      case cod @ CodSoapMessage(_, _, _, _, _, _, _, _, _, _, _)               =>
        codSoapMessageFormatter.writes(cod) ++ Json.obj(
          "status" -> DeliveryStatus.COD.toString()
        )
      case coe @ CoeSoapMessage(_, _, _, _, _, _, _, _, _, _, _)               =>
        coeSoapMessageFormatter.writes(coe) ++ Json.obj(
          "status" -> DeliveryStatus.COE.toString()
        )
    }
  }
  implicit val outboundSoapMessageFormatter: OFormat[OutboundSoapMessage] = OFormat(outboundSoapMessageReads, outboundSoapMessageWrites)
}
