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

import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatformoutboundsoap.models.SoapRequest
import uk.gov.hmrc.apiplatformoutboundsoap.support.{Ccn2Service, WireMockSupport}

class OutboundConnectorISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with WireMockSupport with Ccn2Service {
  override implicit lazy val app: Application = appBuilder.build()

  protected  def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled" -> false,
        "auditing.enabled" -> false
      )

  trait Setup {
    val underTest: OutboundConnector = app.injector.instanceOf[OutboundConnector]
    val messageId = "messageId"
  }


  "postMessage" should {
    val message = SoapRequest("<Envelope><Body>foobar</Body></Envelope>", wireMockBaseUrlAsString)

    "return successful statuses returned by the CCN2 service" in new Setup {
      val expectedStatus: Int = OK
      primeCcn2Endpoint(message.soapEnvelope, expectedStatus)

      val result: Int = await(underTest.postMessage(messageId, message))

      result shouldBe expectedStatus
    }

    "return error statuses returned by the CCN2 service" in new Setup {
      val expectedStatus: Int = INTERNAL_SERVER_ERROR
      primeCcn2Endpoint(message.soapEnvelope, expectedStatus)

      val result: Int = await(underTest.postMessage(messageId, message))

      result shouldBe expectedStatus
    }

    "return error status when soap fault is returned by the CCN2 service" in new Setup {
      Seq(Fault.CONNECTION_RESET_BY_PEER, Fault.EMPTY_RESPONSE, Fault.MALFORMED_RESPONSE_CHUNK,
        Fault.RANDOM_DATA_THEN_CLOSE) foreach { fault =>

        stubCcn2Endpoint(message.soapEnvelope, fault)
        val result: Int = await(underTest.postMessage(messageId, message))
        result shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "send the given message to the CCN2 service" in new Setup {
      primeCcn2Endpoint(message.soapEnvelope, OK)

      await(underTest.postMessage(messageId, message))

      verifyRequestBody(message.soapEnvelope)
    }

    "send the right SOAP content type header" in new Setup {
      primeCcn2Endpoint(message.soapEnvelope, OK)

      await(underTest.postMessage(messageId, message))

      verifyHeader(CONTENT_TYPE, "application/soap+xml")
    }
  }
}
