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

package uk.gov.hmrc.apiplatformoutboundsoap.connectors

import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import scala.concurrent.ExecutionContext.Implicits.global


class OutboundConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure()
    .build()

  trait Setup {
    val mockDefaultHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
  }

  "OutboundConnector" should {
    "use proxy when configured" in new Setup {
      val config: Configuration = app.configuration ++ Configuration("proxy.proxyRequiredForThisEnvironment" -> true)

      val underTest = new OutboundConnector(config, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockProxiedHttpClient
    }

    "use default ws client when proxy is disabled" in new Setup {
      val config: Configuration = app.configuration ++ Configuration("proxy.proxyRequiredForThisEnvironment" -> false)

      val underTest = new OutboundConnector(config, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
    }

    "use default ws client when proxy is not configured" in new Setup {
      val config: Configuration = app.configuration
      val underTest = new OutboundConnector(config, mockDefaultHttpClient, mockProxiedHttpClient)
      underTest.httpClient shouldBe mockDefaultHttpClient
    }

  }
}
