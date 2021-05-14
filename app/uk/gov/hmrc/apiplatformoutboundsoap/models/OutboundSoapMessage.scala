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
  val destinationUrl: String
  val status: StatusType
  val createDateTime: DateTime
  val notificationUrl: Option[String]
  val ccnHttpStatus: Int
  val coeMessage: Option[String]
  val codMessage: Option[String]
}

object OutboundSoapMessage {

  def typeToStatus(fullyQualifiedName: String): StatusType = {

    if (fullyQualifiedName == classTag[SentOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendStatus.SENT
    } else if (fullyQualifiedName == classTag[FailedOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendStatus.FAILED
    } else if (fullyQualifiedName == classTag[RetryingOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendStatus.RETRYING
    } else if (fullyQualifiedName == classTag[CoeSoapMessage].runtimeClass.getCanonicalName) {
      DelStatus.COE
    } else if (fullyQualifiedName == classTag[CodSoapMessage].runtimeClass.getCanonicalName) {
      DelStatus.COD
    } else {
      throw new IllegalArgumentException
    }
  }
}

case class  SentOutboundSoapMessage(globalId: UUID,
                                   messageId: Option[String],
                                   soapMessage: String,
                                   destinationUrl: String,
                                   createDateTime: DateTime,
                                   ccnHttpStatus: Int,
                                   notificationUrl: Option[String] = None,
                                    codMessage: Option[String] = None,
                                    coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendStatus = SendStatus.SENT
}

case class FailedOutboundSoapMessage(globalId: UUID,
                                     messageId: Option[String],
                                     soapMessage: String,
                                     destinationUrl: String,
                                     createDateTime: DateTime,
                                     ccnHttpStatus: Int,
                                     notificationUrl: Option[String] = None,
                                     codMessage: Option[String] = None,
                                     coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendStatus = SendStatus.FAILED
}

case class CoeSoapMessage(globalId: UUID,
                          messageId: Option[String],
                          soapMessage: String,
                          destinationUrl: String,
                          createDateTime: DateTime,
                          ccnHttpStatus: Int,
                          notificationUrl: Option[String] = None,
                          codMessage: Option[String] = None,
                          coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: DelStatus = DelStatus.COE
}

case class CodSoapMessage(globalId: UUID,
                          messageId: Option[String],
                          soapMessage: String,
                          destinationUrl: String,
                          createDateTime: DateTime,
                          ccnHttpStatus: Int,
                          notificationUrl: Option[String] = None,
                          codMessage: Option[String] = None,
                          coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: DelStatus = DelStatus.COD
}

case class RetryingOutboundSoapMessage(globalId: UUID,
                                       messageId: Option[String],
                                       soapMessage: String,
                                       destinationUrl: String,
                                       createDateTime: DateTime,
                                       retryDateTime: DateTime,
                                       ccnHttpStatus: Int,
                                       notificationUrl: Option[String] = None,
                                       codMessage: Option[String] = None,
                                       coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendStatus = SendStatus.RETRYING

  def toFailed = FailedOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus,
    notificationUrl, codMessage, coeMessage)

  def toSent = SentOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus,
    notificationUrl, codMessage, coeMessage)
}

sealed abstract class StatusType extends EnumEntry

object StatusType extends Enum[StatusType] with PlayJsonEnum[StatusType]{
  val values = findValues
}

sealed abstract class DelStatus extends StatusType

object DelStatus extends Enum[DelStatus] with PlayJsonEnum[DelStatus] {
  val values = findValues

  case object COE extends DelStatus

  case object COD extends DelStatus
}

sealed abstract class SendStatus extends StatusType

object SendStatus extends Enum[SendStatus] with PlayJsonEnum[SendStatus] {
  val values = findValues

  case object SENT extends SendStatus

  case object FAILED extends SendStatus

  case object RETRYING extends SendStatus
}

sealed trait DeliveryStatus extends EnumEntry

object DeliveryStatus extends Enum[DeliveryStatus] with PlayJsonEnum[DeliveryStatus] {
  val values: immutable.IndexedSeq[DeliveryStatus] = findValues

  object ConfirmationStatus extends Enum[DeliveryStatus] {
    val values = findValues

    case object COE extends DeliveryStatus

    case object COD extends DeliveryStatus

  }

  object SendingStatus extends Enum[DeliveryStatus] {
    val values = findValues

    case object SENT extends DeliveryStatus

    case object FAILED extends DeliveryStatus

    case object RETRYING extends DeliveryStatus
  }


}
