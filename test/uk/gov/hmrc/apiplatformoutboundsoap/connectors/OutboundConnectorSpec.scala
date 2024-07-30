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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.SoapRequest

class OutboundConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure()
    .build()

  trait Setup extends HttpClientV2MockModule {
    val appConfigMock: AppConfig     = mock[AppConfig]
    val messageId                    = "messageId"
    val soapRequestMock: SoapRequest = mock[SoapRequest]
    when(soapRequestMock.soapEnvelope).thenReturn("<IE4N03>payload</IE4N03>")
    when(soapRequestMock.destinationUrl).thenReturn("http://example.com/some-url")
  }

  "OutboundConnector" should {
    "return valid status code if http post returns 2xx" in new Setup {
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      HttpClientV2Mock.Post.thenReturn(Right(HttpResponse(OK, "")))

      val underTest   = new OutboundConnector(appConfigMock, HttpClientV2Mock.aMock)
      val result: Int = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe OK
      HttpClientV2Mock.Post.verifyNoProxy()
    }

    "return valid status code if http post returns 2xx and use proxy" in new Setup {
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(true)
      HttpClientV2Mock.Post.thenReturn(Right(HttpResponse(OK, "")))

      val underTest   = new OutboundConnector(appConfigMock, HttpClientV2Mock.aMock)
      val result: Int = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe OK
      HttpClientV2Mock.Post.verifyProxy()
    }

    "return valid status code if http post returns 5xx" in new Setup {
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      HttpClientV2Mock.Post.thenReturn(Left(UpstreamErrorResponse("unexpected error", INTERNAL_SERVER_ERROR)))

      val underTest   = new OutboundConnector(appConfigMock, HttpClientV2Mock.aMock)
      val result: Int = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe INTERNAL_SERVER_ERROR
      HttpClientV2Mock.Post.verifyNoProxy()
    }

    "return valid status code if http post returns NonFatal errors" in new Setup {
      when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)
      HttpClientV2Mock.Post.thenFail()

      val underTest   = new OutboundConnector(appConfigMock, HttpClientV2Mock.aMock)
      val result: Int = await(underTest.postMessage(messageId, soapRequestMock))
      result shouldBe INTERNAL_SERVER_ERROR
      HttpClientV2Mock.Post.verifyNoProxy()
    }
  }
}
