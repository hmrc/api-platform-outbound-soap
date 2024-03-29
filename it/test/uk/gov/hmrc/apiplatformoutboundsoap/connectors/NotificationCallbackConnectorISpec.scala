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

package uk.gov.hmrc.apiplatformoutboundsoap.connectors

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.ws.rs.core.MediaType.APPLICATION_JSON

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.soapMessageStatusFormatter
import uk.gov.hmrc.apiplatformoutboundsoap.models.{OutboundSoapMessage, RetryingOutboundSoapMessage, SoapMessageStatus}
import uk.gov.hmrc.apiplatformoutboundsoap.support.{NotificationsService, WireMockSupport}

class NotificationCallbackConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with WireMockSupport with NotificationsService {
  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"  -> false,
        "auditing.enabled" -> false
      )

  trait Setup {
    val underTest: NotificationCallbackConnector = app.injector.instanceOf[NotificationCallbackConnector]
    val globalId: UUID                           = UUID.randomUUID()
    val messageId: String                        = "some message id"
    val now: Instant                             = Instant.now.truncatedTo(ChronoUnit.MILLIS)
    val httpStatus: Int                          = 200
    implicit val hc: HeaderCarrier               = HeaderCarrier()
  }

  "sendNotification" should {
    "successfully send a status update to the caller's notification URL" in new Setup {
      val message: OutboundSoapMessage = RetryingOutboundSoapMessage(
        globalId,
        messageId,
        "<Envelope><Body>foobar</Body></Envelope>",
        "some url",
        now,
        now,
        httpStatus,
        Some(wireMockBaseUrlAsString)
      )
      val expectedStatus: Int          = OK
      primeNotificationsEndpoint(expectedStatus)

      val Some(result) = await(underTest.sendNotification(message))

      result shouldBe expectedStatus
    }

    "not send a status update when notification URL is absent" in new Setup {
      val message: OutboundSoapMessage = RetryingOutboundSoapMessage(
        globalId,
        messageId,
        "<Envelope><Body>foobar</Body></Envelope>",
        "some url",
        now,
        now,
        httpStatus,
        None
      )

      val result: Option[Int] = await(underTest.sendNotification(message))

      result shouldBe None
    }

    "successfully send a status update with a body to the caller's notification URL" in new Setup {
      val message: OutboundSoapMessage     = RetryingOutboundSoapMessage(
        globalId,
        messageId,
        "<Envelope><Body>foobar</Body></Envelope>",
        "some url",
        now,
        now,
        httpStatus,
        Some(wireMockBaseUrlAsString)
      )
      val expectedStatus: Int              = OK
      val expectedNotificationBody: String = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(message)).toString()
      primeNotificationsEndpoint(expectedStatus)

      await(underTest.sendNotification(message))
      verifyRequestBody(expectedNotificationBody)
    }

    "set the Content-Type header to application/json" in new Setup {
      val message: OutboundSoapMessage = RetryingOutboundSoapMessage(
        globalId,
        messageId,
        "<Envelope><Body>foobar</Body></Envelope>",
        "some url",
        now,
        now,
        httpStatus,
        Some(wireMockBaseUrlAsString)
      )
      val expectedStatus: Int          = OK
      primeNotificationsEndpoint(expectedStatus)

      await(underTest.sendNotification(message))
      verifyHeader(CONTENT_TYPE, APPLICATION_JSON)
    }

    "handle failed requests to the notification URL" in new Setup {
      val message: OutboundSoapMessage = RetryingOutboundSoapMessage(
        globalId,
        messageId,
        "<Envelope><Body>foobar</Body></Envelope>",
        "some url",
        now,
        now,
        httpStatus,
        Some(wireMockBaseUrlAsString)
      )
      val expectedStatus: Int          = INTERNAL_SERVER_ERROR
      primeNotificationsEndpoint(expectedStatus)

      val Some(result) = await(underTest.sendNotification(message))

      result shouldBe expectedStatus
    }

    "recover from exceptions" in new Setup {
      val message: OutboundSoapMessage = RetryingOutboundSoapMessage(
        globalId,
        messageId,
        "<Envelope><Body>foobar</Body></Envelope>",
        "some url",
        now,
        now,
        httpStatus,
        Some("https://invalidUrl")
      )

      val result: Option[Int] = await(underTest.sendNotification(message))

      result shouldBe None
    }
  }
}
