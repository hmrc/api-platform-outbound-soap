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

package uk.gov.hmrc.apiplatformoutboundsoap.services

import akka.stream.Materializer
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.cache.AsyncCacheApi
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.{NotificationCallbackConnector, OutboundConnector}
import uk.gov.hmrc.apiplatformoutboundsoap.models.{DeliveryStatus, SentOutboundSoapMessage}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class ConfirmationServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val cache: AsyncCacheApi = app.injector.instanceOf[AsyncCacheApi]

  trait Setup {
    val outboundConnectorMock: OutboundConnector = mock[OutboundConnector]
    val outboundMessageRepositoryMock: OutboundMessageRepository = mock[OutboundMessageRepository]
    val notificationCallbackConnectorMock: NotificationCallbackConnector = mock[NotificationCallbackConnector]
    val appConfigMock: AppConfig = mock[AppConfig]
    val httpStatus: Int = 200

    val expectedCreateDateTime: DateTime = DateTime.now(UTC)
    val expectedGlobalId: UUID = UUID.randomUUID

    val underTest: ConfirmationService = new ConfirmationService(outboundMessageRepository = outboundMessageRepositoryMock,
      notificationCallbackConnector = notificationCallbackConnectorMock)
    }
      "processConfirmation" should {
        val confirmationRequest =
          """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
            |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
            |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
            |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoD</wsa:Action>
            |        <wsa:From>
            |            <wsa:Address>[FROM]</wsa:Address>
            |        </wsa:From>
            |        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/ack">RELATES_TO_ID</wsa:RelatesTo>
            |        <wsa:MessageID>[COD_MESSAGE_ID]</wsa:MessageID>
            |        <wsa:To>[TO]</wsa:To>
            |    </soap:Header>
            |    <soap:Body>
            |        <ccn2:CoD>
            |            <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
            |        </ccn2:CoD>
            |    </soap:Body>
            |</soap:Envelope>""".stripMargin.replaceAll("\n", "")
        val outboundSoapMessage = SentOutboundSoapMessage(UUID.randomUUID, Some("123"), "envelope", "some url", DateTime.now(UTC), OK)


        "update a sent message with a CoD" in new Setup {
          when(outboundMessageRepositoryMock.updateConfirmationStatus("RELATES_TO_ID", DeliveryStatus.COD, confirmationRequest))
            .thenReturn(successful(Some(outboundSoapMessage)))
          underTest.processConfirmation(Some(confirmationRequest), DeliveryStatus.COD)
          verify(outboundMessageRepositoryMock).updateConfirmationStatus("RELATES_TO_ID", DeliveryStatus.COD, confirmationRequest)
        }

        "update a sent message with a CoE" in new Setup {
          when(outboundMessageRepositoryMock.updateConfirmationStatus("RELATES_TO_ID", DeliveryStatus.COE, confirmationRequest))
            .thenReturn(successful(Some(outboundSoapMessage)))
          underTest.processConfirmation(Some(confirmationRequest), DeliveryStatus.COE)
          verify(outboundMessageRepositoryMock).updateConfirmationStatus("RELATES_TO_ID", DeliveryStatus.COE, confirmationRequest)
        }
      }
}