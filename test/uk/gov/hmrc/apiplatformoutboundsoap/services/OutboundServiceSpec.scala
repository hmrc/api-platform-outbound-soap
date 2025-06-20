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
import java.util.UUID.randomUUID
import javax.wsdl.WSDLException
import javax.wsdl.xml.WSDLReader
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.Duration

import com.mongodb.client.result.InsertOneResult
import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.wsdl.WSDLUtil
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source.{fromIterator, single}
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.mongodb.scala.bson.BsonNumber
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName

import play.api.cache.AsyncCacheApi
import play.api.http.Status.OK
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.{NotificationCallbackConnector, OutboundConnector}
import uk.gov.hmrc.apiplatformoutboundsoap.models.SendingStatus.{FAILED, PENDING, RETRYING, SENT}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.apiplatformoutboundsoap.util.TestDataFactory

class OutboundServiceSpec extends AnyWordSpec with TestDataFactory with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val cache: AsyncCacheApi       = app.injector.instanceOf[AsyncCacheApi]

  trait Setup {
    val outboundConnectorMock: OutboundConnector                         = mock[OutboundConnector]
    val outboundMessageRepositoryMock: OutboundMessageRepository         = mock[OutboundMessageRepository]
    val wsSecurityServiceMock: WsSecurityService                         = mock[WsSecurityService]
    val notificationCallbackConnectorMock: NotificationCallbackConnector = mock[NotificationCallbackConnector]
    val wsdlParser: WsdlParser                                           = mock[WsdlParser]
    val appConfigMock: AppConfig                                         = mock[AppConfig]
    val cacheSpy: AsyncCacheApi                                          = spy[AsyncCacheApi](cache)
    val httpStatus: Int                                                  = 200

    when(appConfigMock.retryDuration).thenReturn(retryDuration)
    when(appConfigMock.cacheDuration).thenReturn(Duration("10 min"))
    when(appConfigMock.ccn2Host).thenReturn("example.com")
    when(appConfigMock.ccn2Port).thenReturn(1234)
    when(appConfigMock.addressingFrom).thenReturn("HMRC")
    when(appConfigMock.addressingReplyTo).thenReturn("ReplyTo")
    when(appConfigMock.addressingFaultTo).thenReturn("FaultTo")

    when(appConfigMock.proxyRequiredForThisEnvironment).thenReturn(false)

    val reader: WSDLReader    = WSDLUtil.newWSDLReaderWithPopulatedExtensionRegistry
    reader.setFeature("javax.wsdl.importDocuments", true)
    val definition            = reader.readWSDL("test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl")
    val definitionUrlResolver = reader.readWSDL("test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_URLResolver.wsdl")
    when(wsdlParser.parseWsdl(*)).thenReturn(successful(definition))

    val underTest: OutboundService =
      new OutboundService(outboundConnectorMock, wsSecurityServiceMock, outboundMessageRepositoryMock, notificationCallbackConnectorMock, wsdlParser, appConfigMock, cacheSpy) {}
  }

  "sendMessage" should {
    val to                                = "CCN2"
    val from                              = "HMRC"
    val longPrivateHeaderValue1023Length  = "".padTo(1023, 'a')
    val longPrivateHeaderValue1024Length  = longPrivateHeaderValue1023Length ++ "a"
    val longPrivateHeaderValue1025Length  = longPrivateHeaderValue1024Length ++ "a"
    val longPrivateHeaderName1025Length   = longPrivateHeaderValue1024Length ++ "a"
    val privateHeaders                    = Some(List(PrivateHeader(name = "name1", value = "value1"), PrivateHeader(name = "name2", value = "value2")))
    val addressing                        = Addressing(from, to, "ReplyTo", "FaultTo", messageId, Some("RelatesTo"))
    // mixin refers to mandatory and default addressing fields
    val addressingMixinFields             = Addressing(from = from, to = to, replyTo = "ReplyTo", faultTo = "FaultTo", messageId = messageId)
    val addressingWithEmptyOptionalFields = Addressing(from = from, to = to, replyTo = " ", faultTo = "", messageId = messageId)
    val messageRequestFullAddressing      = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
      "IE4N03notifyERiskAnalysisHit",
      """<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>""",
      addressing = addressing,
      confirmationOfDelivery = Some(false),
      Some("http://somenotification.url")
    )

    val messageRequestUrlResolver = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_URLResolver.wsdl",
      "IE4N03notifyERiskAnalysisHit",
      """<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>""",
      addressing = addressing,
      confirmationOfDelivery = Some(false),
      Some("http://somenotification.url")
    )

    val messageRequestEmptyBody = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
      "IsAlive",
      "",
      addressing = addressing,
      confirmationOfDelivery = Some(false),
      Some("http://somenotification.url")
    )

    val messageRequestWithPrivateHeaders = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
      "IsAlive",
      "",
      addressing = addressing,
      confirmationOfDelivery = Some(false),
      Some("http://somenotification.url"),
      privateHeaders = privateHeaders
    )

    val messageRequestMinimalAddressing            = messageRequestFullAddressing
      .copy(addressing = addressingMixinFields)
    val messageRequestAddressingWithEmptyOptionals = messageRequestFullAddressing
      .copy(addressing = addressingWithEmptyOptionalFields)
    val expectedStatus: Int                        = OK

    val allAddressingHeaders =
      """<wsa:From><wsa:Address>HMRC</wsa:Address></wsa:From>
        |<wsa:To>CCN2</wsa:To>
        |<wsa:ReplyTo><wsa:Address>ReplyTo</wsa:Address></wsa:ReplyTo>
        |<wsa:FaultTo><wsa:Address>FaultTo</wsa:Address></wsa:FaultTo>
        |<wsa:MessageID>123</wsa:MessageID>
        |<wsa:RelatesTo>RelatesTo</wsa:RelatesTo>""".stripMargin.replaceAll("\n", "")

    val mixinAddressingHeaders =
      """<wsa:From><wsa:Address>HMRC</wsa:Address></wsa:From>
        |<wsa:To>CCN2</wsa:To>
        |<wsa:ReplyTo><wsa:Address>ReplyTo</wsa:Address></wsa:ReplyTo>
        |<wsa:FaultTo><wsa:Address>FaultTo</wsa:Address></wsa:FaultTo>
        |<wsa:MessageID>123</wsa:MessageID>""".stripMargin.replaceAll("\n", "")

    val addressingHeadersWithoutOptionals =
      """<wsa:From><wsa:Address>HMRC</wsa:Address></wsa:From>
        |<wsa:To>CCN2</wsa:To>
        |<wsa:MessageID>123</wsa:MessageID>""".stripMargin.replaceAll("\n", "")

    def expectedSoapEnvelope(extraHeaders: String = ""): String =
      s"""<?xml version='1.0' encoding='utf-8'?>
         |<soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://www.w3.org/2005/08/addressing">
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

    def expectedSoapEnvelopeWithEmptyBodyRequest(extraHeaders: String = ""): String =
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
         |<nszPw9y:isAliveReqMsg xmlns:nszPw9y="http://xmlns.ec.eu/BusinessMessages/TATAFng/Monitoring/V1">
         |</nszPw9y:isAliveReqMsg>
         |</soapenv:Body>
         |</soapenv:Envelope>""".stripMargin.replaceAll("\n", "")

    "return the outbound soap message for success" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      val response = await(underTest.sendMessage(messageRequestFullAddressing))
      response.isRight shouldBe true
      response.map { result =>
        result.status shouldBe SendingStatus.SENT
        result.soapMessage shouldBe expectedSoapEnvelope()
        result.messageId shouldBe messageId
        result.globalId shouldBe sentOutboundSoapMessage.globalId
        result.createDateTime shouldBe now
        result.sentDateTime shouldBe Some(shortlyAfterNow)
      }
    }

    "get the WSDL definition from cache" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestFullAddressing))

      verify(cacheSpy).getOrElseUpdate(*, *)(*)(*)
    }

    "return the outbound soap message for failure" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(BAD_REQUEST))
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(failedOutboundSoapMessage)))

      val response = await(underTest.sendMessage(messageRequestFullAddressing))
      response.isRight shouldBe true
      response.map { result =>
        result.status shouldBe SendingStatus.FAILED
        result.soapMessage shouldBe expectedSoapEnvelope()
        result.messageId shouldBe messageId
        result.globalId shouldBe failedOutboundSoapMessage.globalId
        result.createDateTime shouldBe now
      }
    }

    "save the message as SENT when the connector returns 2xx" in new Setup {
      (200 to 299).foreach { httpCode =>
        when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
        when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(httpCode))
        val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
        when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
        when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage.copy(ccnHttpStatus = Some(httpCode)))))
        val result                                             = await(underTest.sendMessage(messageRequestFullAddressing))

        result.isRight shouldBe true
        result.map { msg =>
          msg.status shouldBe SendingStatus.SENT
          msg.soapMessage shouldBe expectedSoapEnvelope()
          msg.messageId shouldBe messageId
          msg.globalId shouldBe sentOutboundSoapMessage.globalId
          msg.createDateTime shouldBe now
          msg.sentDateTime shouldBe Some(shortlyAfterNow)
          msg.ccnHttpStatus shouldBe Some(httpCode)
          msg.notificationUrl shouldBe Some(notificationUrl)
          msg.destinationUrl shouldBe destinationUrl
        }
        messageCaptor.getValue.status shouldBe PENDING
        verify(outboundMessageRepositoryMock).persist(*)
        verify(outboundMessageRepositoryMock).updateSendingStatus(messageCaptor.getValue.globalId, SENT, httpCode)
        reset(outboundMessageRepositoryMock)
      }
    }

    "save the message as FAILED when the connector returns a 3xx, 4xx" in new Setup {
      (300 to 499).foreach { httpCode =>
        when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
        when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(httpCode))
        val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
        when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
        when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(failedOutboundSoapMessage.copy(ccnHttpStatus = Some(httpCode)))))

        val result = await(underTest.sendMessage(messageRequestFullAddressing))
        result.isRight shouldBe true
        result.map { msg =>
          msg.status shouldBe FAILED
          msg.soapMessage shouldBe expectedSoapEnvelope()
          msg.messageId shouldBe messageId
          msg.globalId shouldBe failedOutboundSoapMessage.globalId
          msg.createDateTime shouldBe now
          msg.sentDateTime shouldBe None
          msg.ccnHttpStatus shouldBe Some(httpCode)
          msg.notificationUrl shouldBe messageRequestFullAddressing.notificationUrl
          msg.destinationUrl shouldBe destinationUrl
        }
        verify(outboundMessageRepositoryMock).persist(*)
        verify(outboundMessageRepositoryMock).updateSendingStatus(messageCaptor.getValue.globalId, FAILED, httpCode)
        reset(outboundMessageRepositoryMock)

      }
    }

    "save the message as RETRYING when the connector returns a 5xx" in new Setup {
      (500 to 599).foreach { httpCode =>
        when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
        when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(httpCode))
        val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
        when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
        when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(retryingOutboundSoapMessage.copy(ccnHttpStatus =
          Some(httpCode)
        ))))
        when(appConfigMock.retryInterval).thenReturn(expectedInterval)

        val result = await(underTest.sendMessage(messageRequestFullAddressing))
        result.isRight shouldBe true
        result.map { msg =>
          msg.status shouldBe SendingStatus.RETRYING
          msg.soapMessage shouldBe expectedSoapEnvelope()
          msg.messageId shouldBe messageId
          msg.globalId shouldBe retryingOutboundSoapMessage.globalId
          msg.createDateTime shouldBe retryingOutboundSoapMessage.createDateTime
          msg.asInstanceOf[RetryingOutboundSoapMessage].retryDateTime shouldBe expectedRetryAt
          msg.notificationUrl shouldBe messageRequestFullAddressing.notificationUrl
          msg.destinationUrl shouldBe destinationUrl
        }
        verify(outboundMessageRepositoryMock).persist(*)
        verify(outboundMessageRepositoryMock).updateSendingStatus(messageCaptor.getValue.globalId, RETRYING, httpCode)
        reset(outboundMessageRepositoryMock)
      }
    }

    "send the SOAP envelope returned from the security service to the connector" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      val messageCaptor: ArgumentCaptor[SoapRequest] = ArgumentCaptor.forClass(classOf[SoapRequest])
      when(outboundConnectorMock.postMessage(*, messageCaptor.capture())).thenReturn(successful(expectedStatus))

      await(underTest.sendMessage(messageRequestFullAddressing))

      getXmlDiff(messageCaptor.getValue.soapEnvelope, expectedSoapEnvelope()).build().hasDifferences shouldBe false
      messageCaptor.getValue.destinationUrl shouldBe "http://example.com:1234/CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS"
    }

    "resolve destination url not having port when sending the SOAP envelope returned from the security service to the connector" in new Setup {
      when(wsdlParser.parseWsdl(*)).thenReturn(successful(definitionUrlResolver))
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))
      val messageCaptor: ArgumentCaptor[SoapRequest] = ArgumentCaptor.forClass(classOf[SoapRequest])
      when(outboundConnectorMock.postMessage(*, messageCaptor.capture())).thenReturn(successful(expectedStatus))

      await(underTest.sendMessage(messageRequestUrlResolver))
      messageCaptor.getValue.destinationUrl shouldBe "http://example.com/CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS"
    }

    "send the SOAP envelope with empty body returned from the security service to the connector" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelopeWithEmptyBodyRequest())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(200))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))
      await(underTest.sendMessage(messageRequestEmptyBody))

      messageCaptor.getValue.status shouldBe SendingStatus.PENDING
      messageCaptor.getValue.soapMessage shouldBe expectedSoapEnvelopeWithEmptyBodyRequest()
      messageCaptor.getValue.messageId shouldBe messageId
      verify(outboundMessageRepositoryMock).updateSendingStatus(messageCaptor.getValue.globalId, SENT, 200)
    }

    "send the expected SOAP envelope to the security service which adds username token" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(mixinAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestMinimalAddressing))
      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(mixinAddressingHeaders)).build().getDifferences.forEach(println)
      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(mixinAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "send the expected SOAP envelope to the security service which adds signature" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(appConfigMock.enableMessageSigning).thenReturn(true)
      when(wsSecurityServiceMock.addSignature(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(mixinAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestMinimalAddressing))

      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(mixinAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "send the optional addressing headers if present in the request" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope]        = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      val persistCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(mixinAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(persistCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestMinimalAddressing))
      persistCaptor.getValue.soapMessage shouldBe expectedSoapEnvelope(mixinAddressingHeaders)
      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(mixinAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "not send the optional addressing headers if they are empty in the request" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope]        = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      val persistCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(addressingHeadersWithoutOptionals))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(persistCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestAddressingWithEmptyOptionals))
      persistCaptor.getValue.soapMessage shouldBe expectedSoapEnvelope(addressingHeadersWithoutOptionals)
      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(addressingHeadersWithoutOptionals)).build().hasDifferences shouldBe false
    }

    "persist message ID if present in the request for success" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestMinimalAddressing))

      messageCaptor.getValue.messageId shouldBe messageId
    }

    "persist message ID if present in the request for failure" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(sentOutboundSoapMessage)))

      await(underTest.sendMessage(messageRequestMinimalAddressing))

      messageCaptor.getValue.messageId shouldBe messageId
    }

    "fail when the given operation does not exist in the WSDL definition" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))

      val exception: NotFoundException = intercept[NotFoundException] {
        await(underTest.sendMessage(messageRequestFullAddressing.copy(wsdlOperation = "missingOperation")))
      }

      exception.message shouldBe "Operation missingOperation not found"
    }

    "fail when the given WSDL does not exist" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(wsdlParser.parseWsdl(*)).thenReturn(failed(new WSDLException(WSDLException.INVALID_WSDL, "This file was not found: http://localhost:3001/")))

      val exception: WSDLException = intercept[WSDLException] {
        await(underTest.sendMessage(messageRequestFullAddressing.copy(wsdlUrl = "http://localhost:3001/")))
      }

      exception.getMessage should include("This file was not found: http://localhost:3001/")
    }

    "fail when the addressing.to field is empty" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException] {
        await(underTest.sendMessage(messageRequestFullAddressing.copy(addressing = addressing.copy(to = ""))))
      }

      exception.getMessage should include("addressing.to being empty")
    }

    "fail when the addressing.messageId field is empty" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException] {
        await(underTest.sendMessage(messageRequestFullAddressing.copy(addressing = addressing.copy(messageId = ""))))
      }

      exception.getMessage should include("addressing.messageId being empty")
    }

    "fail when the addressing.from field is empty" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException] {
        await(underTest.sendMessage(messageRequestFullAddressing.copy(addressing = addressing.copy(from = ""))))
      }

      exception.getMessage should include("addressing.from being empty")
    }

    "fail when the private headers field value is greater than 1024 chars" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException] {
        await(underTest.sendMessage(messageRequestWithPrivateHeaders.copy(privateHeaders =
          Some(List(PrivateHeader(name = "name1", value = longPrivateHeaderValue1025Length), PrivateHeader(name = "name2", value = "value2")))
        )))
      }
      exception.getMessage should include("privateHeaders value is longer than 1024 characters")
    }

    "fail when the private headers field name is greater than 1024 chars" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException] {
        await(underTest.sendMessage(messageRequestWithPrivateHeaders.copy(privateHeaders =
          Some(List(PrivateHeader(name = longPrivateHeaderName1025Length, value = "value1"), PrivateHeader(name = "name2", value = "value2")))
        )))
      }
      exception.getMessage should include("privateHeaders name is longer than 1024 characters")
    }

    "persist message when the private headers field value is exactly 1024 chars" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(failedOutboundSoapMessage)))
      await(underTest.sendMessage(messageRequestWithPrivateHeaders.copy(privateHeaders =
        Some(List(PrivateHeader(name = "name1", value = longPrivateHeaderValue1024Length), PrivateHeader(name = "name2", value = "value2")))
      )))
      messageCaptor.getValue.messageId shouldBe messageId
    }

    "persist message when the private headers field parameters size is less than 1024" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(expectedStatus))
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())).thenReturn(Future(InsertOneResult.acknowledged(BsonNumber(1))))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(Future(Some(failedOutboundSoapMessage)))
      await(underTest.sendMessage(messageRequestWithPrivateHeaders.copy(privateHeaders =
        Some(List(PrivateHeader(name = "name1", value = longPrivateHeaderValue1023Length), PrivateHeader(name = "name2", value = "value2")))
      )))
      messageCaptor.getValue.messageId shouldBe messageId
    }
  }

  "retryMessages" should {
    "retry a message successfully" in new Setup {
      val testScopedGlobalId = globalId
      val retryingMessage    = RetryingOutboundSoapMessage(testScopedGlobalId, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", now, now, Some(httpStatus))
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateToSentWhereNotConfirmed(*, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(single(retryingMessage))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock, never).updateSendingStatus(*, *, *)
      verify(outboundMessageRepositoryMock).updateToSentWhereNotConfirmed(refEq(testScopedGlobalId), *)
    }

    "abort retrying messages if unexpected exception thrown" in new Setup {
      val retryingMessage        = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", null, "some url", Instant.now, Instant.now, Some(httpStatus))
      val anotherRetryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload 2</IE4N03>", "another url", Instant.now, Instant.now, Some(httpStatus))
      when(appConfigMock.parallelism).thenReturn(2)
      when(outboundConnectorMock.postMessage(anotherRetryingMessage.messageId, SoapRequest(anotherRetryingMessage.soapMessage, anotherRetryingMessage.destinationUrl))).thenReturn(
        successful(OK)
      )
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage, anotherRetryingMessage).iterator))

      intercept[Exception](await(underTest.retryMessages))

      verify(outboundMessageRepositoryMock, never).updateSendingStatus(anotherRetryingMessage.globalId, SendingStatus.SENT)
    }

    "retry a message and persist with retrying when SOAP request returned error status and retry duration not yet passed" in new Setup {
      val testScopedGlobalId = globalId
      val retryingMessage    = RetryingOutboundSoapMessage(testScopedGlobalId, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", now, now, Some(httpStatus))
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))
      when(outboundMessageRepositoryMock.updateNextRetryTime(refEq(testScopedGlobalId), *)).thenReturn(successful(None))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).retrieveMessagesForRetry
      verify(outboundMessageRepositoryMock).updateNextRetryTime(refEq(testScopedGlobalId), *)
    }

    "retry a message and mark failed when SOAP request received 1xx status" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", Instant.now, Instant.now, Some(httpStatus))
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(CONTINUE))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(successful(None))
      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock, never).updateNextRetryTime(*, *)
      verify(outboundMessageRepositoryMock, never).updateSendingStatus(retryingMessage.globalId, SendingStatus.SENT)
      verify(outboundMessageRepositoryMock, never).updateSendingStatus(retryingMessage.globalId, SendingStatus.RETRYING)
      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED, CONTINUE)
    }

    "retry a message and mark failed when SOAP request received 3xx status" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", Instant.now, Instant.now, Some(httpStatus))
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(TEMPORARY_REDIRECT))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(successful(None))
      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock, never).updateNextRetryTime(*, *)
      verify(outboundMessageRepositoryMock, never).updateSendingStatus(retryingMessage.globalId, SendingStatus.SENT)
      verify(outboundMessageRepositoryMock, never).updateSendingStatus(retryingMessage.globalId, SendingStatus.RETRYING)
      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED, TEMPORARY_REDIRECT)
    }

    "retry a message and mark failed when SOAP request received 4xx status" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", Instant.now, Instant.now, Some(httpStatus))
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(NOT_FOUND))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(successful(None))
      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock, never).updateNextRetryTime(*, *)
      verify(outboundMessageRepositoryMock, never).updateSendingStatus(retryingMessage.globalId, SendingStatus.SENT)
      verify(outboundMessageRepositoryMock, never).updateSendingStatus(retryingMessage.globalId, SendingStatus.RETRYING)
      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED, NOT_FOUND)
    }

    "set a message's status to FAILED when its retryDuration has expired" in new Setup {
      val retryDuration: Duration = Duration("30s")
      val retryingMessage         = RetryingOutboundSoapMessage(
        randomUUID,
        "MessageId-A1",
        "<IE4N03>payload</IE4N03>",
        "some url",
        Instant.now.minus(java.time.Duration.ofMillis(retryDuration.plus(Duration("1s")).toMillis)),
        Instant.now,
        Some(httpStatus)
      )
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED, INTERNAL_SERVER_ERROR)
      verify(outboundMessageRepositoryMock, never).updateNextRetryTime(*, *)
    }

    "notify the caller on the notification URL supplied that the retrying message is now SENT" in new Setup {
      val retryingMessage                                     = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", Instant.now, Instant.now, Some(httpStatus))
      val sentMessageForNotification: SentOutboundSoapMessage = retryingMessage.toSent
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateToSentWhereNotConfirmed(*, *)).thenReturn(successful(Some(sentMessageForNotification)))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))

      await(underTest.retryMessages)

      verify(notificationCallbackConnectorMock).sendNotification(refEq(sentMessageForNotification))(*)
    }

    "notify the caller on the notification URL supplied that the retrying message is now FAILED" in new Setup {
      val retryDuration: Duration                                 = Duration("30s")
      val retryingMessage                                         = RetryingOutboundSoapMessage(
        randomUUID,
        "MessageId-A1",
        "<IE4N03>payload</IE4N03>",
        "some url",
        Instant.now.minus(java.time.Duration.ofMillis(retryDuration.plus(Duration("1s")).toMillis)),
        Instant.now,
        Some(httpStatus)
      )
      val failedMessageForNotification: FailedOutboundSoapMessage = retryingMessage.toFailed
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *, *)).thenReturn(successful(Some(failedMessageForNotification)))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))

      await(underTest.retryMessages)

      verify(notificationCallbackConnectorMock).sendNotification(refEq(failedMessageForNotification))(*)
    }

    "update the status of a successfully sent message to SENT even if calling the notification URL fails" in new Setup {
      val testScopedGlobalId                                      = globalId
      val retryDuration: Duration                                 = Duration("30s")
      val retryingMessage                                         = RetryingOutboundSoapMessage(
        testScopedGlobalId,
        "MessageId-A1",
        "<IE4N03>payload</IE4N03>",
        "some url",
        Instant.now.minus(java.time.Duration.ofMillis(retryDuration.plus(Duration("1s")).toMillis)),
        Instant.now,
        Some(httpStatus)
      )
      val failedMessageForNotification: FailedOutboundSoapMessage = retryingMessage.toFailed
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*, *)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateToSentWhereNotConfirmed(*, *)).thenReturn(successful(Some(failedMessageForNotification)))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).thenReturn(fromIterator(() => Seq(retryingMessage).iterator))
      when(notificationCallbackConnectorMock.sendNotification(*)(*)).thenReturn(successful(Some(INTERNAL_SERVER_ERROR)))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).updateToSentWhereNotConfirmed(refEq(testScopedGlobalId), any[Instant])

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
