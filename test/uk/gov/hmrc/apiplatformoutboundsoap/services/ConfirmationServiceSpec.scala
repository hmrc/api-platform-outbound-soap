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

package uk.gov.hmrc.apiplatformoutboundsoap.services

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.xml.Elem

import org.apache.pekko.stream.Materializer
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.cache.AsyncCacheApi
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.{NotificationCallbackConnector, OutboundConnector}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.models.common.{MessageIdNotFoundResult, UpdateResult, UpdateSuccessResult}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository

class ConfirmationServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val cache: AsyncCacheApi       = app.injector.instanceOf[AsyncCacheApi]

  trait Setup {
    val outboundConnectorMock: OutboundConnector                         = mock[OutboundConnector]
    val outboundMessageRepositoryMock: OutboundMessageRepository         = mock[OutboundMessageRepository]
    val notificationCallbackConnectorMock: NotificationCallbackConnector = mock[NotificationCallbackConnector]
    val appConfigMock: AppConfig                                         = mock[AppConfig]
    val httpStatus: Int                                                  = 200

    val expectedGlobalId: UUID = UUID.randomUUID

    val underTest: ConfirmationService =
      new ConfirmationService(outboundMessageRepository = outboundMessageRepositoryMock, notificationCallbackConnector = notificationCallbackConnectorMock)
  }

  "processConfirmation" should {
    val confirmationRequestCod: Elem = xml.XML.loadString(
      """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
        |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoD</wsa:Action>
        |        <wsa:From>
        |            <wsa:Address>[FROM]</wsa:Address>
        |        </wsa:From>
        |        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/ack">abcd1234</wsa:RelatesTo>
        |        <wsa:MessageID>[COD_MESSAGE_ID]</wsa:MessageID>
        |        <wsa:To>[TO]</wsa:To>
        |    </soap:Header>
        |    <soap:Body>
        |        <ccn2:CoD>
        |            <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
        |        </ccn2:CoD>
        |    </soap:Body>
        |</soap:Envelope>""".stripMargin.replaceAll("\n", "")
    )

    val outboundSoapMessage = SentOutboundSoapMessage(UUID.randomUUID, "123", "envelope", "some url", Instant.now, OK)
    val msgId: String       = "abcd1234"

    "update a sent message with a CoD" in new Setup {
      when(outboundMessageRepositoryMock.findById(*)).thenReturn(successful(Some(outboundSoapMessage)))
      when(outboundMessageRepositoryMock.updateConfirmationStatus(*, *, *)).thenReturn(successful(Some(outboundSoapMessage)))
      val result: UpdateResult = await(underTest.processConfirmation(confirmationRequestCod, msgId, DeliveryStatus.COD))
      result shouldBe UpdateSuccessResult
      verify(outboundMessageRepositoryMock).findById("abcd1234")
      verify(outboundMessageRepositoryMock).updateConfirmationStatus("abcd1234", DeliveryStatus.COD, confirmationRequestCod.toString())
    }

    "update a sent message with a CoE" in new Setup {
      when(outboundMessageRepositoryMock.findById(*)).thenReturn(successful(Some(outboundSoapMessage)))
      when(outboundMessageRepositoryMock.updateConfirmationStatus(*, *, *)).thenReturn(successful(Some(outboundSoapMessage)))
      val result: UpdateResult = await(underTest.processConfirmation(confirmationRequestCod, msgId, DeliveryStatus.COE))
      result shouldBe UpdateSuccessResult
      verify(outboundMessageRepositoryMock).updateConfirmationStatus("abcd1234", DeliveryStatus.COE, confirmationRequestCod.toString())
    }

    "return failure for RelatesTo ID which cannot be found" in new Setup {
      when(outboundMessageRepositoryMock.findById(*)).thenReturn(successful(Option.empty[SentOutboundSoapMessage]))
      when(outboundMessageRepositoryMock.updateConfirmationStatus(*, *, *)).thenReturn(successful(Option.empty[SentOutboundSoapMessage]))
      val result: UpdateResult = await(underTest.processConfirmation(confirmationRequestCod, msgId, DeliveryStatus.COE))
      result shouldBe MessageIdNotFoundResult
    }

    "send update to notification URL" in new Setup {
      when(outboundMessageRepositoryMock.findById(*)).thenReturn(successful(Some(outboundSoapMessage)))
      when(outboundMessageRepositoryMock.updateConfirmationStatus(*, *, *)).thenReturn(successful(Option(outboundSoapMessage)))
      await(underTest.processConfirmation(confirmationRequestCod, msgId, DeliveryStatus.COE))
      verify(notificationCallbackConnectorMock).sendNotification(refEq(outboundSoapMessage))(*)
    }
  }
}
