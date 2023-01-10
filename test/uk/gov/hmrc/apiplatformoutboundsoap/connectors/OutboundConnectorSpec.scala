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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.SoapRequest

class OutboundConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure()
    .build()

  trait Setup {
    val appConfigMock: AppConfig                 = mock[AppConfig]
    val mockDefaultHttpClient: HttpClient        = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val messageId                                = "messageId"
  }

  "OutboundConnector" should {
    "use proxy when configured" in new Setup {
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(true)
      val underTest = new OutboundConnector(appConfigMock, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockProxiedHttpClient
    }

    "use default ws client when proxy is disabled" in new Setup {
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      val underTest = new OutboundConnector(appConfigMock, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
    }

    "use default ws client when proxy is not configured" in new Setup {
      val underTest = new OutboundConnector(appConfigMock, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
    }

    "return valid status code if http post returns 2xx" in new Setup {
      val soapRequestMock: SoapRequest = mock[SoapRequest]
      when(soapRequestMock.soapEnvelope).thenReturn("<IE4N03>payload</IE4N03>")
      when(soapRequestMock.destinationUrl).thenReturn("some url")
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *))
        .thenReturn(successful(Right(HttpResponse(OK, ""))))
      val underTest                    = new OutboundConnector(appConfigMock, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
      val result: Int                  = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe OK
    }

    "return valid status code if http post returns 5xx" in new Setup {
      val soapRequestMock: SoapRequest = mock[SoapRequest]
      when(soapRequestMock.soapEnvelope).thenReturn("<IE4N03>payload</IE4N03>")
      when(soapRequestMock.destinationUrl).thenReturn("some url")
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("unexpected error", INTERNAL_SERVER_ERROR))))
      val underTest                    = new OutboundConnector(appConfigMock, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
      val result: Int                  = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe INTERNAL_SERVER_ERROR
    }

    "return valid status code if http post returns NonFatal errors" in new Setup {
      val soapRequestMock: SoapRequest = mock[SoapRequest]
      when(soapRequestMock.soapEnvelope).thenReturn("<IE4N03>payload</IE4N03>")
      when(soapRequestMock.destinationUrl).thenReturn("some url")
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      when(mockDefaultHttpClient.POSTString[Either[UpstreamErrorResponse, HttpResponse]](*, *, *)(*, *, *))
        .thenReturn(failed(play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException.INSTANCE))
      val underTest                    = new OutboundConnector(appConfigMock, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
      val result: Int                  = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
