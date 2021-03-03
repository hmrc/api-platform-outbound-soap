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
import scala.reflect.classTag

sealed trait OutboundSoapMessage {
  val globalId: UUID
  val messageId: Option[String]
  val soapMessage: String
  val status: SendingStatus
  val createDateTime: DateTime
  val notificationUrl: Option[String]
  val ccnHttpStatus: Int
}

object OutboundSoapMessage {

  def typeToStatus (fullyQualifiedName: String): SendingStatus = {

    if (fullyQualifiedName == classTag[SentOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendingStatus.SENT
    } else if (fullyQualifiedName == classTag[FailedOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendingStatus.FAILED
    } else if (fullyQualifiedName == classTag[RetryingOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendingStatus.RETRYING
    } else {
      throw new IllegalArgumentException
    }
  }
}

case class SentOutboundSoapMessage(globalId: UUID,
                                   messageId: Option[String],
                                   soapMessage: String,
                                   createDateTime: DateTime,
                                   ccnHttpStatus: Int,
                                   notificationUrl: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.SENT
}

case class FailedOutboundSoapMessage(globalId: UUID,
                                     messageId: Option[String],
                                     soapMessage: String,
                                     createDateTime: DateTime,
                                     ccnHttpStatus: Int,
                                     notificationUrl: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.FAILED
}

case class RetryingOutboundSoapMessage(globalId: UUID,
                                       messageId: Option[String],
                                       soapMessage: String,
                                       createDateTime: DateTime,
                                       retryDateTime: DateTime,
                                       ccnHttpStatus: Int,
                                       notificationUrl: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.RETRYING

  def toFailed = FailedOutboundSoapMessage(globalId, messageId, soapMessage, createDateTime, ccnHttpStatus, notificationUrl)
  def toSent = SentOutboundSoapMessage(globalId, messageId, soapMessage, createDateTime, ccnHttpStatus, notificationUrl)
}

sealed trait SendingStatus extends EnumEntry

object SendingStatus extends Enum[SendingStatus] with PlayJsonEnum[SendingStatus] {
  val values: immutable.IndexedSeq[SendingStatus] = findValues

  case object SENT extends SendingStatus

  case object FAILED extends SendingStatus

  case object RETRYING extends SendingStatus

}
