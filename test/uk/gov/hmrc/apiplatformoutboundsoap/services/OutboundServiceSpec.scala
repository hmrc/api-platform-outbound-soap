/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.wsdl.WSDLException
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.OK
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.{Addressing, MessageRequest}
import uk.gov.hmrc.http.NotFoundException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class OutboundServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val outboundConnectorMock: OutboundConnector = mock[OutboundConnector]
    val underTest = new OutboundService(outboundConnectorMock)
  }

  "sendMessage" should {
    val messageRequest = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
      "IE4N03notifyERiskAnalysisHit",
      """<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>"""
    )
    val messageRequestWithAddressing = messageRequest
      .copy(addressing = Some(Addressing(Some("HMRC"), Some("CCN2"), Some("HMRC_reply"), Some("HMRC_fault"), Some("123"), Some("foobar"))))
    val expectedStatus: Int = OK

    val optionalAddressingHeaders =
      """<wsa:From xmlns:wsa="http://www.w3.org/2005/08/addressing">HMRC</wsa:From>
        |<wsa:To xmlns:wsa="http://www.w3.org/2005/08/addressing">CCN2</wsa:To>
        |<wsa:ReplyTo xmlns:wsa="http://www.w3.org/2005/08/addressing">HMRC_reply</wsa:ReplyTo>
        |<wsa:FaultTo xmlns:wsa="http://www.w3.org/2005/08/addressing">HMRC_fault</wsa:FaultTo>
        |<wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">123</wsa:MessageID>
        |<wsa:RelatesTo xmlns:wsa="http://www.w3.org/2005/08/addressing">foobar</wsa:RelatesTo>""".stripMargin.replaceAll("\n", "")

    def expectedSoapEnvelope(extraHeaders: String = ""): String =
      s"""<?xml version='1.0' encoding='utf-8'?>
        |<soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
          |<soapenv:Header>
            |<wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS/IE4N03notifyERiskAnalysisHit</wsa:Action>
            |$extraHeaders
          |</soapenv:Header>
          |<soapenv:Body>
            |<nsGHXkF:IE4N03notifyERiskAnalysisHitReqMsg xmlns:nsGHXkF="http://xmlns.ec.eu/BusinessActivityService/ICS/IRiskAnalysisOrchestrationBAS/V1">
              |<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>
            |</nsGHXkF:IE4N03notifyERiskAnalysisHitReqMsg>
          |</soapenv:Body>
        |</soapenv:Envelope>""".stripMargin.replaceAll("\n", "")

    "return the status returned by the outbound connector" in new Setup {
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val result: Int = await(underTest.sendMessage(messageRequest))

      result shouldBe expectedStatus
    }

    "send the expected SOAP envelope to the connector" in new Setup {
      val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(outboundConnectorMock.postMessage(messageCaptor.capture())).thenReturn(successful(expectedStatus))

      await(underTest.sendMessage(messageRequest))

      messageCaptor.getValue shouldBe expectedSoapEnvelope()
    }

    "send the optional addressing headers if present in the request" in new Setup {
      val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(outboundConnectorMock.postMessage(messageCaptor.capture())).thenReturn(successful(expectedStatus))

      await(underTest.sendMessage(messageRequestWithAddressing))

      messageCaptor.getValue shouldBe expectedSoapEnvelope(optionalAddressingHeaders)
    }

    "fail when the given operation does not exist in the WSDL definition" in new Setup {
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val exception: NotFoundException = intercept[NotFoundException]{
        await(underTest.sendMessage(messageRequest.copy(wsdlOperation = "missingOperation")))
      }

      exception.message shouldBe "Operation missingOperation not found"
    }

    "fail when the given WSDL does not exist" in new Setup {
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val exception: WSDLException = intercept[WSDLException]{
        await(underTest.sendMessage(messageRequest.copy(wsdlUrl = "http://example.com/missing")))
      }

      exception.getMessage should include ("This file was not found: http://example.com/missing")
    }
  }
}
