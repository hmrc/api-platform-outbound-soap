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

package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsObject, JsPath, Json, JsonConfiguration, JsonNaming, OFormat, OWrites, Reads}
import uk.gov.hmrc.apiplatformoutboundsoap.models.{CodSoapMessage, CoeSoapMessage, FailedOutboundSoapMessage, OutboundSoapMessage, RetryingOutboundSoapMessage, SendingStatus, SentOutboundSoapMessage}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats


private[repositories] object MongoFormatter extends MongoJavatimeFormats{

  implicit val cfg = JsonConfiguration(
    discriminator = "status",

    typeNaming = JsonNaming { fullName =>
      OutboundSoapMessage.typeToStatus(fullName).entryName
    })

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  
  implicit val retryingMessageReads: Reads[RetryingOutboundSoapMessage] =
    Json.reads[RetryingOutboundSoapMessage]
  implicit val retryingMessageWrites: OWrites[RetryingOutboundSoapMessage] =
    Json.writes[RetryingOutboundSoapMessage].transform(_ ++ Json.obj("status" -> "RETRYING"))
  implicit val retryingSoapMessageFormatter: OFormat[RetryingOutboundSoapMessage] =
    OFormat(retryingMessageReads, retryingMessageWrites)
  
  implicit val sentMessageReads: Reads[SentOutboundSoapMessage] =
    Json.reads[SentOutboundSoapMessage]
  implicit val sentMessageWrites: OWrites[SentOutboundSoapMessage] =
    Json.writes[SentOutboundSoapMessage].transform(_ ++ Json.obj("status" -> "SENT"))
  implicit val sentSoapMessageFormatter: OFormat[SentOutboundSoapMessage] =
    OFormat(sentMessageReads, sentMessageWrites)
  
  implicit val failedMessageReads: Reads[FailedOutboundSoapMessage] =
    Json.reads[FailedOutboundSoapMessage]
  implicit val failedMessageWrites: OWrites[FailedOutboundSoapMessage] =
    Json.writes[FailedOutboundSoapMessage].transform(_ ++ Json.obj("status" -> "FAILED"))
  implicit val failedSoapMessageFormatter: OFormat[FailedOutboundSoapMessage] =
    OFormat(failedMessageReads, failedMessageWrites)
  
 implicit val codMessageReads: Reads[CodSoapMessage] =
    Json.reads[CodSoapMessage]
  implicit val codMessageWrites: OWrites[CodSoapMessage] =
    Json.writes[CodSoapMessage].transform(_ ++ Json.obj("status" -> "COD"))
  implicit val codSoapMessageFormatter: OFormat[CodSoapMessage] =
    OFormat(codMessageReads, codMessageWrites)
  
 implicit val coeMessageReads: Reads[CoeSoapMessage] =
    Json.reads[CoeSoapMessage]
  implicit val coeMessageWrites: OWrites[CoeSoapMessage] =
    Json.writes[CoeSoapMessage].transform(_ ++ Json.obj("status" -> "COE"))
  implicit val coeSoapMessageFormatter: OFormat[CoeSoapMessage] =
    OFormat(coeMessageReads, coeMessageWrites)
  

  implicit val outboundSoapMessageReads: Reads[OutboundSoapMessage] =
    (JsPath \ "status").read[String].flatMap {
      case "RETRYING" =>
        retryingSoapMessageFormatter.widen[OutboundSoapMessage]
      case "SENT" =>
        sentSoapMessageFormatter.widen[OutboundSoapMessage]
      case "FAILED" =>
        failedSoapMessageFormatter.widen[OutboundSoapMessage]
      case "COD" =>
        codSoapMessageFormatter.widen[OutboundSoapMessage]
      case "COE" =>
        coeSoapMessageFormatter.widen[OutboundSoapMessage]
    }

  implicit val outboundSoapMessageWrites: OWrites[OutboundSoapMessage] = new OWrites[OutboundSoapMessage] {
    override def writes(soapMessage: OutboundSoapMessage): JsObject = soapMessage match {
      case r @ RetryingOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _) =>
        retryingSoapMessageFormatter.writes(r) ++ Json.obj(
          "status" -> "RETRYING"
        )
      case f @ FailedOutboundSoapMessage(_, _, _, _, _, _, _, _, _) =>
        failedSoapMessageFormatter.writes(f) ++ Json.obj(
          "status" -> "FAILED"
        )
    }
  }
    implicit val outboundSoapMessageFormatter: OFormat[OutboundSoapMessage] = OFormat(outboundSoapMessageReads, outboundSoapMessageWrites)
}
