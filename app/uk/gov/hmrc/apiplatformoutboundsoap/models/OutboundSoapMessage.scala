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
  val status: DeliveryStatus
  val createDateTime: DateTime
  val notificationUrl: Option[String]
  val ccnHttpStatus: Int
}

object OutboundSoapMessage {

  def typeToStatus(fullyQualifiedName: String): DeliveryStatus = {

    if (fullyQualifiedName == classTag[SentOutboundSoapMessage].runtimeClass.getCanonicalName) {
      DeliveryStatus.SENT
    } else if (fullyQualifiedName == classTag[FailedOutboundSoapMessage].runtimeClass.getCanonicalName) {
      DeliveryStatus.FAILED
    } else if (fullyQualifiedName == classTag[RetryingOutboundSoapMessage].runtimeClass.getCanonicalName) {
      DeliveryStatus.RETRYING
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
                                   messageId: Option[String],
                                   soapMessage: String,
                                   destinationUrl: String,
                                   createDateTime: DateTime,
                                   ccnHttpStatus: Int,
                                   notificationUrl: Option[String] = None) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.SENT
}

case class FailedOutboundSoapMessage(globalId: UUID,
                                     messageId: Option[String],
                                     soapMessage: String,
                                     destinationUrl: String,
                                     createDateTime: DateTime,
                                     ccnHttpStatus: Int,
                                     notificationUrl: Option[String] = None) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.FAILED
}

case class CoeSoapMessage(globalId: UUID,
                          messageId: Option[String],
                          soapMessage: String,
                          destinationUrl: String,
                          createDateTime: DateTime,
                          ccnHttpStatus: Int,
                          notificationUrl: Option[String] = None,
                          coeMessage: String) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.COE
}

case class CodSoapMessage(globalId: UUID,
                          messageId: Option[String],
                          soapMessage: String,
                          destinationUrl: String,
                          createDateTime: DateTime,
                          ccnHttpStatus: Int,
                          notificationUrl: Option[String] = None,
                          codMessage: String) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.COD
}

case class RetryingOutboundSoapMessage(globalId: UUID,
                                       messageId: Option[String],
                                       soapMessage: String,
                                       destinationUrl: String,
                                       createDateTime: DateTime,
                                       retryDateTime: DateTime,
                                       ccnHttpStatus: Int,
                                       notificationUrl: Option[String] = None) extends OutboundSoapMessage {
  override val status: DeliveryStatus = DeliveryStatus.RETRYING

  def toFailed = FailedOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus, notificationUrl)

  def toSent = SentOutboundSoapMessage(globalId, messageId, soapMessage, destinationUrl, createDateTime, ccnHttpStatus, notificationUrl)
}

sealed trait DeliveryStatus extends EnumEntry

object DeliveryStatus extends Enum[DeliveryStatus] with PlayJsonEnum[DeliveryStatus] {
  val values: immutable.IndexedSeq[DeliveryStatus] = findValues

  case object SENT extends DeliveryStatus

  case object FAILED extends DeliveryStatus

  case object RETRYING extends DeliveryStatus

  case object COE extends DeliveryStatus

  case object COD extends DeliveryStatus

}
