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
  val messageId: String
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
      throw new IllegalArgumentException
    }
  }
}


case class SentOutboundSoapMessage(globalId: UUID,
                                   messageId: String,
                                   soapMessage: String,
                                   destinationUrl: String,
                                   createDateTime: DateTime,
                                   ccnHttpStatus: Int,
                                   notificationUrl: Option[String] = None,
                                    codMessage: Option[String] = None,
                                    coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.SENT
}

case class FailedOutboundSoapMessage(globalId: UUID,
                                     messageId: String,
                                     soapMessage: String,
                                     destinationUrl: String,
                                     createDateTime: DateTime,
                                     ccnHttpStatus: Int,
                                     notificationUrl: Option[String] = None,
                                     codMessage: Option[String] = None,
                                     coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.FAILED
}

case class CoeSoapMessage(globalId: UUID,
                          messageId: String,
                          soapMessage: String,
                          destinationUrl: String,
                          createDateTime: DateTime,
                          ccnHttpStatus: Int,
                          notificationUrl: Option[String] = None,
                          codMessage: Option[String] = None,
                          coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.COE
}

case class CodSoapMessage(globalId: UUID,
                          messageId: String,
                          soapMessage: String,
                          destinationUrl: String,
                          createDateTime: DateTime,
                          ccnHttpStatus: Int,
                          notificationUrl: Option[String] = None,
                          codMessage: Option[String] = None,
                          coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.COD
}

case class RetryingOutboundSoapMessage(globalId: UUID,
                                       messageId: String,
                                       soapMessage: String,
                                       destinationUrl: String,
                                       createDateTime: DateTime,
                                       retryDateTime: DateTime,
                                       ccnHttpStatus: Int,
                                       notificationUrl: Option[String] = None,
                                       codMessage: Option[String] = None,
                                       coeMessage: Option[String] = None) extends OutboundSoapMessage {
  override val status: SendingStatus = SendingStatus.RETRYING

  def toFailed = FailedOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus,
    notificationUrl, codMessage, coeMessage)

  def toSent = SentOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus,
    notificationUrl, codMessage, coeMessage)
}

sealed abstract class StatusType extends EnumEntry

object StatusType extends Enum[StatusType] with PlayJsonEnum[StatusType]{
  val values = findValues
}

sealed abstract class DeliveryStatus(override val entryName: String) extends StatusType

object DeliveryStatus extends Enum[DeliveryStatus] with PlayJsonEnum[DeliveryStatus] {
  val values: immutable.IndexedSeq[DeliveryStatus] = findValues

  case object COE extends DeliveryStatus("CCN2.Service.Platform.AcknowledgementService/CoE")

  case object COD extends DeliveryStatus("CCN2.Service.Platform.AcknowledgementService/CoD")
}

sealed abstract class SendingStatus extends StatusType

object SendingStatus extends Enum[SendingStatus] with PlayJsonEnum[SendingStatus] {
  val values: immutable.IndexedSeq[SendingStatus] = findValues

  case object SENT extends SendingStatus

  case object FAILED extends SendingStatus

  case object RETRYING extends SendingStatus
}
