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
import play.api.libs.json.{Format, Json, JsonConfiguration, JsonNaming, OFormat}
import uk.gov.hmrc.apiplatformoutboundsoap.models.{CodSoapMessage, CoeSoapMessage, FailedOutboundSoapMessage, OutboundSoapMessage, RetryingOutboundSoapMessage, SentOutboundSoapMessage}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

private[repositories] object MongoFormatter {

  implicit val cfg = JsonConfiguration(
    discriminator = "status",

    typeNaming = JsonNaming { fullName =>
      OutboundSoapMessage.typeToStatus(fullName).entryName
    })

  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val outboundSoapMessageFormatter: OFormat[OutboundSoapMessage] = Json.format[OutboundSoapMessage]
  implicit val retryingSoapMessageFormatter: OFormat[RetryingOutboundSoapMessage] = Json.format[RetryingOutboundSoapMessage]
  implicit val sentSoapMessageFormatter: OFormat[SentOutboundSoapMessage] = Json.format[SentOutboundSoapMessage]
  implicit val failedSoapMessageFormatter: OFormat[FailedOutboundSoapMessage] = Json.format[FailedOutboundSoapMessage]
  implicit val coeSoapMessageFormatter: OFormat[CoeSoapMessage] = Json.format[CoeSoapMessage]
  implicit val codSoapMessageFormatter: OFormat[CodSoapMessage] = Json.format[CodSoapMessage]
}
