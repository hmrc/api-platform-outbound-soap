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

import java.time.Instant
import java.util.UUID
import scala.reflect.classTag

import play.api.libs.json.Format

sealed trait OutboundSoapMessage {
  val globalId: UUID
  val messageId: String
  val soapMessage: String
  val destinationUrl: String
  val status: StatusType
  val createDateTime: Instant
  val notificationUrl: Option[String]
  val ccnHttpStatus: Int
  val coeMessage: Option[String]
  val codMessage: Option[String]
  val sentDateTime: Option[Instant]
  val privateHeaders: Option[List[PrivateHeader]]
}

object OutboundSoapMessage {

  def typeToStatus(fullyQualifiedName: String): StatusType = {

    if (fullyQualifiedName == classTag[SentOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendingStatus.SENT
    } else if (fullyQualifiedName == classTag[FailedOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendingStatus.FAILED
    } else if (fullyQualifiedName == classTag[RetryingOutboundSoapMessage].runtimeClass.getCanonicalName) {
      SendingStatus.RETRYING
    } else if (fullyQualifiedName == classTag[CoeSoapMessage].runtimeClass.getCanonicalName) {
      DeliveryStatus.COE
    } else if (fullyQualifiedName == classTag[CodSoapMessage].runtimeClass.getCanonicalName) {
      DeliveryStatus.COD
    } else {
      throw new IllegalArgumentException(s"${fullyQualifiedName} is not a valid class")
    }
  }
}

case class SentOutboundSoapMessage(
    globalId: UUID,
    messageId: String,
    soapMessage: String,
    destinationUrl: String,
    createDateTime: Instant,
    ccnHttpStatus: Int,
    notificationUrl: Option[String] = None,
    codMessage: Option[String] = None,
    coeMessage: Option[String] = None,
    sentDateTime: Option[Instant] = None,
    privateHeaders: Option[List[PrivateHeader]] = None
  ) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.SENT
}

case class FailedOutboundSoapMessage(
    globalId: UUID,
    messageId: String,
    soapMessage: String,
    destinationUrl: String,
    createDateTime: Instant,
    ccnHttpStatus: Int,
    notificationUrl: Option[String] = None,
    codMessage: Option[String] = None,
    coeMessage: Option[String] = None,
    sentDateTime: Option[Instant] = None,
    privateHeaders: Option[List[PrivateHeader]] = None
  ) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.FAILED
}

case class CoeSoapMessage(
    globalId: UUID,
    messageId: String,
    soapMessage: String,
    destinationUrl: String,
    createDateTime: Instant,
    ccnHttpStatus: Int,
    notificationUrl: Option[String] = None,
    codMessage: Option[String] = None,
    coeMessage: Option[String] = None,
    sentDateTime: Option[Instant] = None,
    privateHeaders: Option[List[PrivateHeader]] = None
  ) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.COE
}

case class CodSoapMessage(
    globalId: UUID,
    messageId: String,
    soapMessage: String,
    destinationUrl: String,
    createDateTime: Instant,
    ccnHttpStatus: Int,
    notificationUrl: Option[String] = None,
    codMessage: Option[String] = None,
    coeMessage: Option[String] = None,
    sentDateTime: Option[Instant] = None,
    privateHeaders: Option[List[PrivateHeader]] = None
  ) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.COD
}

case class RetryingOutboundSoapMessage(
    globalId: UUID,
    messageId: String,
    soapMessage: String,
    destinationUrl: String,
    createDateTime: Instant,
    retryDateTime: Instant,
    ccnHttpStatus: Int,
    notificationUrl: Option[String] = None,
    codMessage: Option[String] = None,
    coeMessage: Option[String] = None,
    sentDateTime: Option[Instant] = None,
    privateHeaders: Option[List[PrivateHeader]] = None
  ) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.RETRYING

  def toFailed = FailedOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus, notificationUrl, codMessage, coeMessage)

  def toSent = SentOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus, notificationUrl, codMessage, coeMessage, Some(Instant.now))
}

sealed abstract trait StatusType

object StatusType {
  val values: Set[StatusType] = Set(DeliveryStatus.COE, DeliveryStatus.COD, SendingStatus.SENT, SendingStatus.FAILED, SendingStatus.RETRYING)

  def apply(text: String): Option[StatusType] = StatusType.values.find(_.toString() == text.toUpperCase)

  implicit val format: Format[StatusType] = SealedTraitJsonFormatting.createFormatFor[StatusType]("Status Type", apply)
}
// sealed abstract class StatusType extends EnumEntry

// object StatusType extends Enum[StatusType] with PlayJsonEnum[StatusType] {
//   val values: immutable.IndexedSeq[StatusType] = findValues
// }

sealed trait DeliveryStatus extends StatusType

object DeliveryStatus {
  case object COE extends DeliveryStatus
  case object COD extends DeliveryStatus
  val values: Set[DeliveryStatus] = Set(COE, COD)

  def apply(text: String): Option[DeliveryStatus] = DeliveryStatus.values.find(_.toString() == text.toUpperCase)

  def unsafeApply(text: String): DeliveryStatus = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid Delivery Status"))

  def fromAction(action: String): DeliveryStatus = {
    action match {
      case "CCN2.Service.Platform.AcknowledgementService/CoE" => DeliveryStatus.COE
      case "CCN2.Service.Platform.AcknowledgementService/CoD" => DeliveryStatus.COD
      case _                                                  => throw new IllegalArgumentException(s"${action} is not a valid Delivery Status")
    }
  }

  implicit val format: Format[DeliveryStatus] = SealedTraitJsonFormatting.createFormatFor[DeliveryStatus]("Delivery Status", apply)
}
// sealed abstract class DeliveryStatus(override val entryName: String) extends StatusType

// object DeliveryStatus extends Enum[DeliveryStatus] with PlayJsonEnum[DeliveryStatus] {

//   def fromAction(action: String): DeliveryStatus   = {
//     action match {
//       case "CCN2.Service.Platform.AcknowledgementService/CoE" => DeliveryStatus.COE
//       case "CCN2.Service.Platform.AcknowledgementService/CoD" => DeliveryStatus.COD
//       case _                                                  => throw new IllegalArgumentException(s"${action} is not a valid DeliveryStatus")
//     }
//   }
//   val values: immutable.IndexedSeq[DeliveryStatus] = findValues

//   case object COE extends DeliveryStatus("COE")

//   case object COD extends DeliveryStatus("COD")
// }

sealed trait SendingStatus extends StatusType

object SendingStatus {
  case object SENT     extends SendingStatus
  case object FAILED   extends SendingStatus
  case object RETRYING extends SendingStatus
  val values: Set[SendingStatus] = Set(SENT, FAILED, RETRYING)

  def apply(text: String): Option[SendingStatus] = SendingStatus.values.find(_.toString() == text.toUpperCase)

  def unsafeApply(text: String): SendingStatus = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid Sending Status"))

  implicit val format: Format[SendingStatus] = SealedTraitJsonFormatting.createFormatFor[SendingStatus]("Sending Status", apply)
}

// sealed abstract class SendingStatus(override val entryName: String) extends StatusType

// object SendingStatus extends Enum[SendingStatus] with PlayJsonEnum[SendingStatus] {
//   val values: immutable.IndexedSeq[SendingStatus] = findValues

//   case object SENT extends SendingStatus("SENT")

//   case object FAILED extends SendingStatus("FAILED")

//   case object RETRYING extends SendingStatus("RETRYING")
// }
