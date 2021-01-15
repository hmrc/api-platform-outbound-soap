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

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import org.joda.time.DateTime

import java.util.UUID
import scala.collection.immutable

case class OutboundSoapMessage(globalId: UUID,
                               messageId: Option[String],
                               soapMessage: String,
                               status: SendingStatus,
                               createDateTime: DateTime,
                               retryDateTime: Option[DateTime])

sealed trait SendingStatus extends EnumEntry

object SendingStatus extends Enum[SendingStatus] with PlayJsonEnum[SendingStatus] {
  val values: immutable.IndexedSeq[SendingStatus] = findValues

  case object SENT extends SendingStatus
  case object FAILED extends SendingStatus
  case object RETRYING extends SendingStatus
}
