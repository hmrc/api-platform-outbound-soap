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

import org.apache.axiom.soap.SOAPEnvelope
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName
import play.api.http.Status.OK
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.{Addressing, MessageRequest, OutboundSoapMessage, SendingStatus}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.NotFoundException

import java.util.UUID
import javax.wsdl.WSDLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class OutboundServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val outboundConnectorMock: OutboundConnector = mock[OutboundConnector]
    val outboundMessageRepositoryMock: OutboundMessageRepository = mock[OutboundMessageRepository]
    val wsSecurityServiceMock: WsSecurityService = mock[WsSecurityService]
    val appConfigMock: AppConfig = mock[AppConfig]

    val expectedCreateDateTime: DateTime = DateTime.now(UTC)
    val expectedGlobalId: UUID = UUID.randomUUID

    val underTest: OutboundService = new OutboundService(outboundConnectorMock, wsSecurityServiceMock, outboundMessageRepositoryMock, appConfigMock) {
      override def now: DateTime = expectedCreateDateTime
      override def randomUUID: UUID = expectedGlobalId
    }

  }

  "sendMessage" should {
    val messageRequest = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
      "IE4N03notifyERiskAnalysisHit",
      """<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>""",
      None,
      confirmationOfDelivery = false
    )
    val messageId = Some("123")
    val messageRequestWithAddressing = messageRequest
      .copy(addressing = Some(Addressing(Some("HMRC"), Some("CCN2"), Some("HMRC_reply"), Some("HMRC_fault"), messageId, Some("foobar"))))
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
            |<ccnm:MessageHeader xmlns:ccnm="http://ccn2.ec.eu/CCN2.Service.Platform.Common.Schema">
              |<ccnm:Version>1.0</ccnm:Version>
              |<ccnm:SendingDateAndTime>2020-04-30T12:15:58.000Z</ccnm:SendingDateAndTime>
              |<ccnm:MessageType>CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS/IE4N03notifyERiskAnalysisHit</ccnm:MessageType>
              |<ccnm:RequestCoD>false</ccnm:RequestCoD>
            |</ccnm:MessageHeader>
            |$extraHeaders
          |</soapenv:Header>
          |<soapenv:Body>
            |<nsGHXkF:IE4N03notifyERiskAnalysisHitReqMsg xmlns:nsGHXkF="http://xmlns.ec.eu/BusinessActivityService/ICS/IRiskAnalysisOrchestrationBAS/V1">
              |<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>
            |</nsGHXkF:IE4N03notifyERiskAnalysisHitReqMsg>
          |</soapenv:Body>
        |</soapenv:Envelope>""".stripMargin.replaceAll("\n", "")

    "return the outbound soap message for success" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      val result: OutboundSoapMessage = await(underTest.sendMessage(messageRequest))

      result.status shouldBe SendingStatus.SENT
      result.soapMessage shouldBe expectedSoapEnvelope()
      result.messageId shouldBe None
      result.globalId shouldBe expectedGlobalId
      result.createDateTime shouldBe expectedCreateDateTime
    }

    "return the outbound soap message for failure" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(BAD_REQUEST))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      val result: OutboundSoapMessage = await(underTest.sendMessage(messageRequest))

      result.status shouldBe SendingStatus.RETRYING
      result.soapMessage shouldBe expectedSoapEnvelope()
      result.messageId shouldBe None
      result.globalId shouldBe expectedGlobalId
      result.createDateTime shouldBe expectedCreateDateTime
    }

    "save the message as SENT when the connector returns 2XX" in new Setup {
      (200 to 299).foreach { httpCode =>
        when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
        when(outboundConnectorMock.postMessage(*)).thenReturn(successful(httpCode))
        val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
        when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))

        await(underTest.sendMessage(messageRequest))

        messageCaptor.getValue.status shouldBe SendingStatus.SENT
        messageCaptor.getValue.soapMessage shouldBe expectedSoapEnvelope()
        messageCaptor.getValue.messageId shouldBe None
        messageCaptor.getValue.globalId shouldBe expectedGlobalId
        messageCaptor.getValue.createDateTime shouldBe expectedCreateDateTime
      }
    }

    "save the message as RETRYING when the connector returns a non 2XX" in new Setup {
      (300 to 599).foreach { httpCode =>
        when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
        when(outboundConnectorMock.postMessage(*)).thenReturn(successful(httpCode))
        val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
        when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))

        await(underTest.sendMessage(messageRequest))

        messageCaptor.getValue.status shouldBe SendingStatus.RETRYING
        messageCaptor.getValue.soapMessage shouldBe expectedSoapEnvelope()
        messageCaptor.getValue.messageId shouldBe None
        messageCaptor.getValue.globalId shouldBe expectedGlobalId
        messageCaptor.getValue.createDateTime shouldBe expectedCreateDateTime
      }
    }

    "send the SOAP envelope returned from the security service to the connector" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))
      val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(outboundConnectorMock.postMessage(messageCaptor.capture())).thenReturn(successful(expectedStatus))

      await(underTest.sendMessage(messageRequest))

      getXmlDiff(messageCaptor.getValue, expectedSoapEnvelope()).build().hasDifferences shouldBe false
    }

    "send the expected SOAP envelope to the connector" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequest))

      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope()).build().hasDifferences shouldBe false
    }

    "send the optional addressing headers if present in the request" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(optionalAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequestWithAddressing))

      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(optionalAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "persist message ID if present in the request for success" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(optionalAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequestWithAddressing))

      messageCaptor.getValue.messageId shouldBe messageId
    }

    "persist message ID if present in the request for failure" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(optionalAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequestWithAddressing))

      messageCaptor.getValue.messageId shouldBe messageId
    }

    "fail when the given operation does not exist in the WSDL definition" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val exception: NotFoundException = intercept[NotFoundException]{
        await(underTest.sendMessage(messageRequest.copy(wsdlOperation = "missingOperation")))
      }

      exception.message shouldBe "Operation missingOperation not found"
    }

    "fail when the given WSDL does not exist" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val exception: WSDLException = intercept[WSDLException]{
        await(underTest.sendMessage(messageRequest.copy(wsdlUrl = "http://example.com/missing")))
      }

      exception.getMessage should include ("This file was not found: http://example.com/missing")
    }
  }

  private def getXmlDiff(actual: String, expected: String): DiffBuilder = {
    compare(expected)
      .withTest(actual)
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .withNodeFilter(node => !"ccnm:SendingDateAndTime".equalsIgnoreCase(node.getNodeName))
      .checkForIdentical
      .ignoreWhitespace
  }
}
