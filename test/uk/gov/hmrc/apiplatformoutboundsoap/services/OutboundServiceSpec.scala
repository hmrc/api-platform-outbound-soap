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
import akka.stream.scaladsl.Source.{fromFutureSource, fromIterator}
import org.apache.axiom.soap.SOAPEnvelope
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
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
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.{NotificationCallbackConnector, OutboundConnector}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import java.util.UUID
import java.util.UUID.randomUUID
import javax.wsdl.WSDLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.concurrent.duration.Duration

class OutboundServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val cache: AsyncCacheApi = app.injector.instanceOf[AsyncCacheApi]

  trait Setup {
    val outboundConnectorMock: OutboundConnector = mock[OutboundConnector]
    val outboundMessageRepositoryMock: OutboundMessageRepository = mock[OutboundMessageRepository]
    val wsSecurityServiceMock: WsSecurityService = mock[WsSecurityService]
    val notificationCallbackConnectorMock: NotificationCallbackConnector = mock[NotificationCallbackConnector]
    val appConfigMock: AppConfig = mock[AppConfig]
    val cacheSpy: AsyncCacheApi = spy[AsyncCacheApi](cache)
    val httpStatus: Int = 200

    val expectedCreateDateTime: DateTime = DateTime.now(UTC)
    val expectedGlobalId: UUID = UUID.randomUUID
    when(appConfigMock.cacheDuration).thenReturn(Duration("10 min"))

    val underTest: OutboundService = new OutboundService(outboundConnectorMock, wsSecurityServiceMock,
      outboundMessageRepositoryMock, notificationCallbackConnectorMock, appConfigMock, cacheSpy) {
      override def now: DateTime = expectedCreateDateTime

      override def randomUUID: UUID = expectedGlobalId
    }
  }

  "sendMessage" should {
    val messageId = "123"
    val to = "CCN2"
    val messageRequest = MessageRequest(
      "test/resources/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
      "IE4N03notifyERiskAnalysisHit",
      """<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>""",
      Addressing(from = None, to = to, messageId = messageId),
      confirmationOfDelivery = false,
      Some("http://somenotification.url")
    )
    val messageRequestWithAddressing = messageRequest
      .copy(addressing = Addressing(Some("HMRC"), to, Some("TBC"), Some("HMRC_fault"), messageId, Some("foobar")))
    val expectedStatus: Int = OK

    val allAddressingHeaders =
      """<wsa:From xmlns:wsa="http://www.w3.org/2005/08/addressing">HMRC</wsa:From>
        |<wsa:To xmlns:wsa="http://www.w3.org/2005/08/addressing">CCN2</wsa:To>
        |<wsa:ReplyTo xmlns:wsa="http://www.w3.org/2005/08/addressing">TBC</wsa:ReplyTo>
        |<wsa:FaultTo xmlns:wsa="http://www.w3.org/2005/08/addressing">HMRC_fault</wsa:FaultTo>
        |<wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">123</wsa:MessageID>
        |<wsa:RelatesTo xmlns:wsa="http://www.w3.org/2005/08/addressing">foobar</wsa:RelatesTo>""".stripMargin.replaceAll("\n", "")

    val mandatoryAddressingHeaders =
      """<wsa:To xmlns:wsa="http://www.w3.org/2005/08/addressing">CCN2</wsa:To>
        |<wsa:ReplyTo xmlns:wsa="http://www.w3.org/2005/08/addressing">TBC</wsa:ReplyTo>
        |<wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">123</wsa:MessageID>""".stripMargin.replaceAll("\n", "")

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
      result.messageId shouldBe messageId
      result.globalId shouldBe expectedGlobalId
      result.createDateTime shouldBe expectedCreateDateTime
    }

    "get the WSDL definition from cache" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequest))

      verify(cacheSpy).getOrElseUpdate(*, *)(*)(*)
    }

    "return the outbound soap message for failure" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(BAD_REQUEST))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))
      val expectedInterval = Duration("10s")
      when(appConfigMock.retryInterval).thenReturn(expectedInterval)

      val result: OutboundSoapMessage = await(underTest.sendMessage(messageRequest))

      result.status shouldBe SendingStatus.RETRYING
      result.soapMessage shouldBe expectedSoapEnvelope()
      result.messageId shouldBe messageId
      result.globalId shouldBe expectedGlobalId
      result.createDateTime shouldBe expectedCreateDateTime
      result.asInstanceOf[RetryingOutboundSoapMessage].retryDateTime shouldBe expectedCreateDateTime.plus(expectedInterval.toMillis)
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
        messageCaptor.getValue.messageId shouldBe messageId
        messageCaptor.getValue.globalId shouldBe expectedGlobalId
        messageCaptor.getValue.createDateTime shouldBe expectedCreateDateTime
        messageCaptor.getValue.notificationUrl shouldBe messageRequest.notificationUrl
        messageCaptor.getValue.destinationUrl shouldBe "http://example.com/CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS"
      }
    }

    "save the message as RETRYING when the connector returns a non 2XX" in new Setup {
      (300 to 599).foreach { httpCode =>
        when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
        when(outboundConnectorMock.postMessage(*)).thenReturn(successful(httpCode))
        val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
        when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))
        val expectedInterval = Duration("10s")
        when(appConfigMock.retryInterval).thenReturn(expectedInterval)

        await(underTest.sendMessage(messageRequest))

        messageCaptor.getValue.status shouldBe SendingStatus.RETRYING
        messageCaptor.getValue.soapMessage shouldBe expectedSoapEnvelope()
        messageCaptor.getValue.messageId shouldBe messageId
        messageCaptor.getValue.globalId shouldBe expectedGlobalId
        messageCaptor.getValue.createDateTime shouldBe expectedCreateDateTime
        messageCaptor.getValue.asInstanceOf[RetryingOutboundSoapMessage].retryDateTime shouldBe
          expectedCreateDateTime.plus(expectedInterval.toMillis)
        messageCaptor.getValue.notificationUrl shouldBe messageRequest.notificationUrl
        messageCaptor.getValue.destinationUrl shouldBe "http://example.com/CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS"
      }
    }

    "send the SOAP envelope returned from the security service to the connector" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))
      val messageCaptor: ArgumentCaptor[SoapRequest] = ArgumentCaptor.forClass(classOf[SoapRequest])
      when(outboundConnectorMock.postMessage(messageCaptor.capture())).thenReturn(successful(expectedStatus))

      await(underTest.sendMessage(messageRequest))

      getXmlDiff(messageCaptor.getValue.soapEnvelope, expectedSoapEnvelope()).build().hasDifferences shouldBe false
      messageCaptor.getValue.destinationUrl shouldBe "http://example.com/CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS"
    }

    "send the expected SOAP envelope to the security service which adds username token" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(mandatoryAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequest))

      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(mandatoryAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "send the expected SOAP envelope to the security service which adds signature" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(appConfigMock.enableMessageSigning).thenReturn(true)
      when(wsSecurityServiceMock.addSignature(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(mandatoryAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequest))

      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(mandatoryAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "send the optional addressing headers if present in the request" in new Setup {
      val messageCaptor: ArgumentCaptor[SOAPEnvelope] = ArgumentCaptor.forClass(classOf[SOAPEnvelope])
      when(wsSecurityServiceMock.addUsernameToken(messageCaptor.capture())).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      when(outboundMessageRepositoryMock.persist(*)(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequestWithAddressing))

      getXmlDiff(messageCaptor.getValue.toString, expectedSoapEnvelope(allAddressingHeaders)).build().hasDifferences shouldBe false
    }

    "persist message ID if present in the request for success" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequestWithAddressing))

      messageCaptor.getValue.messageId shouldBe messageId
    }

    "persist message ID if present in the request for failure" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope(allAddressingHeaders))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      val messageCaptor: ArgumentCaptor[OutboundSoapMessage] = ArgumentCaptor.forClass(classOf[OutboundSoapMessage])
      when(outboundMessageRepositoryMock.persist(messageCaptor.capture())(*)).thenReturn(successful(()))

      await(underTest.sendMessage(messageRequestWithAddressing))

      messageCaptor.getValue.messageId shouldBe messageId
    }

    "fail when the given operation does not exist in the WSDL definition" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val exception: NotFoundException = intercept[NotFoundException] {
        await(underTest.sendMessage(messageRequest.copy(wsdlOperation = "missingOperation")))
      }

      exception.message shouldBe "Operation missingOperation not found"
    }

    "fail when the given WSDL does not exist" in new Setup {
      when(wsSecurityServiceMock.addUsernameToken(*)).thenReturn(expectedSoapEnvelope())
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val exception: WSDLException = intercept[WSDLException] {
        await(underTest.sendMessage(messageRequest.copy(wsdlUrl = "http://example.com/missing")))
      }

      exception.getMessage should include("This file was not found: http://example.com/missing")
    }
  }

  "retryMessages" should {
    "retry a message successfully" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>",
        "some url", DateTime.now(UTC), DateTime.now(UTC), httpStatus)
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage).toIterator))))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.SENT)
    }

    "abort retrying messages if unexpected exception thrown" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", null, "some url", DateTime.now(UTC),
        DateTime.now(UTC), httpStatus)
      val anotherRetryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1",
        "<IE4N03>payload 2</IE4N03>", "another url", DateTime.now(UTC), DateTime.now(UTC), httpStatus)
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryDuration).thenReturn(Duration("1s"))
      when(outboundConnectorMock.postMessage(SoapRequest(anotherRetryingMessage.soapMessage, anotherRetryingMessage.destinationUrl)))
        .thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage, anotherRetryingMessage).toIterator))))

      intercept[Exception](await(underTest.retryMessages))

      verify(outboundMessageRepositoryMock, never).updateSendingStatus(anotherRetryingMessage.globalId, SendingStatus.SENT)
    }

    "retry a message and persist with retrying status when SOAP request returned error status" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>",
        "some url", DateTime.now(UTC), DateTime.now(UTC), httpStatus)
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryDuration).thenReturn(Duration("5s"))
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.updateNextRetryTime(*, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage).toIterator))))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).updateNextRetryTime(retryingMessage.globalId, expectedCreateDateTime.plus(appConfigMock.retryInterval.toMillis))
    }

    "set a message's status to FAILED when its retryDuration has expired" in new Setup {
      val retryDuration: Duration = Duration("30s")
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>",
        "some url", DateTime.now(UTC).minus(retryDuration.plus(Duration("1s")).toMillis), DateTime.now(UTC), httpStatus)
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(appConfigMock.retryDuration).thenReturn(retryDuration)
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(None))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage).toIterator))))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED)
      verify(outboundMessageRepositoryMock, never).updateNextRetryTime(*, *)
    }

    "notify the caller on the notification URL supplied that the retrying message is now SENT" in new Setup {
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>",
        "some url", DateTime.now(UTC), DateTime.now(UTC), httpStatus)
      val sentMessageForNotification: SentOutboundSoapMessage = retryingMessage.toSent
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("1s"))
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(Some(sentMessageForNotification)))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage).toIterator))))

      await(underTest.retryMessages)

      verify(notificationCallbackConnectorMock).sendNotification(refEq(sentMessageForNotification))(*)
    }

    "notify the caller on the notification URL supplied that the retrying message is now FAILED" in new Setup {
      val retryDuration: Duration = Duration("30s")
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>",
        "some url", DateTime.now(UTC).minus(retryDuration.plus(Duration("1s")).toMillis), DateTime.now(UTC), httpStatus)
      val failedMessageForNotification: FailedOutboundSoapMessage = retryingMessage.toFailed
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(appConfigMock.retryDuration).thenReturn(retryDuration)
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(INTERNAL_SERVER_ERROR))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(Some(failedMessageForNotification)))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage).toIterator))))

      await(underTest.retryMessages)

      verify(notificationCallbackConnectorMock).sendNotification(refEq(failedMessageForNotification))(*)
    }

    "update the status of a successfully sent message to SENT even if calling the notification URL fails" in new Setup {
      val retryDuration: Duration = Duration("30s")
      val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>",
        "some url", DateTime.now(UTC).minus(retryDuration.plus(Duration("1s")).toMillis), DateTime.now(UTC), httpStatus)
      val failedMessageForNotification: FailedOutboundSoapMessage = retryingMessage.toFailed
      when(appConfigMock.parallelism).thenReturn(2)
      when(appConfigMock.retryInterval).thenReturn(Duration("5s"))
      when(appConfigMock.retryDuration).thenReturn(retryDuration)
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(OK))
      when(outboundMessageRepositoryMock.updateSendingStatus(*, *)).thenReturn(successful(Some(failedMessageForNotification)))
      when(outboundMessageRepositoryMock.retrieveMessagesForRetry).
        thenReturn(fromFutureSource(successful(fromIterator(() => Seq(retryingMessage).toIterator))))
      when(notificationCallbackConnectorMock.sendNotification(*)(*)).thenReturn(successful(Some(INTERNAL_SERVER_ERROR)))

      await(underTest.retryMessages)

      verify(outboundMessageRepositoryMock).updateSendingStatus(retryingMessage.globalId, SendingStatus.SENT)

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
