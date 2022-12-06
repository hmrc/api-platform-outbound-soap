/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.apache.axiom.om.OMAbstractFactory.{getOMFactory, getSOAP12Factory}
import org.apache.axiom.om._
import org.apache.axiom.om.util.AXIOMUtil.stringToOM
import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.addressing.AddressingConstants.Final.{WSA_NAMESPACE, WSAW_NAMESPACE}
import org.apache.axis2.addressing.AddressingConstants._
import org.apache.axis2.wsdl.WSDLUtil
import play.api.cache.AsyncCacheApi
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.apiplatformoutboundsoap.CcnRequestResult
import uk.gov.hmrc.apiplatformoutboundsoap.CcnRequestResult._
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.{NotificationCallbackConnector, OutboundConnector}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, NotFoundException}

import java.time.{Duration, Instant}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import javax.wsdl.extensions.soap12.SOAP12Address
import javax.wsdl.xml.WSDLReader
import javax.wsdl.{Definition, Operation, Part, Port, PortType, Service}
import javax.xml.namespace.QName
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundService @Inject()(outboundConnector: OutboundConnector,
                                wsSecurityService: WsSecurityService,
                                outboundMessageRepository: OutboundMessageRepository,
                                notificationCallbackConnector: NotificationCallbackConnector,
                                appConfig: AppConfig,
                                cache: AsyncCacheApi)
                               (implicit val ec: ExecutionContext, mat: Materializer)
  extends HttpErrorFunctions with Logging {

  def now: Instant = Instant.now

  def randomUUID: UUID = UUID.randomUUID

  def sendMessage(message: MessageRequest): Future[OutboundSoapMessage] = {
    for {
      soapRequest <- buildSoapRequest(message)
      httpStatus <- outboundConnector.postMessage(message.addressing.messageId, soapRequest)
      outboundSoapMessage = processSendingResult(message, soapRequest, httpStatus)
      _ <- logCcnSendResult(outboundSoapMessage, httpStatus)
      _ <- outboundMessageRepository.persist(outboundSoapMessage)
    } yield outboundSoapMessage
  }

  def retryMessages(implicit hc: HeaderCarrier): Future[Done] = {
    outboundMessageRepository.retrieveMessagesForRetry.runWith(Sink.foreachAsync[RetryingOutboundSoapMessage](appConfig.parallelism)(retryMessage))
  }

  private def retryMessage(message: RetryingOutboundSoapMessage)(implicit hc: HeaderCarrier): Future[Unit] = {
    val nextRetryInstant: Instant = now.plus(Duration.ofMillis(appConfig.retryInterval.toMillis))
    val globalId = message.globalId
    val messageId = message.messageId
    def updateStatusAndNotify(newStatus: SendingStatus)(implicit hc: HeaderCarrier) = {
      outboundMessageRepository.updateSendingStatus(globalId, newStatus) map { updatedMessage =>
        updatedMessage.map(notificationCallbackConnector.sendNotification)
        ()
      }
    }

    def updateToSentAndNotify(sentInstant: Instant)(implicit hc: HeaderCarrier) = {
      outboundMessageRepository.updateToSent(globalId, sentInstant) map { updatedMessage =>
        updatedMessage.map(notificationCallbackConnector.sendNotification)
        ()
      }
    }

    def retryDurationExpired = {
      message.createDateTime.plus(Duration.ofMillis(appConfig.retryDuration.toMillis)).isBefore(now)
    }

    outboundConnector.postMessage(messageId, SoapRequest(message.soapMessage, message.destinationUrl)) flatMap { httpStatus =>
      mapHttpStatusCode(httpStatus) match {
        case SUCCESS =>
          logSuccess(httpStatus, globalId, messageId)
          updateToSentAndNotify(now)
        case UNEXPECTED_SUCCESS =>
          logSuccess(httpStatus, globalId, messageId)
          updateToSentAndNotify(now)
        case FAIL_ERROR =>
          logSendingFailure(httpStatus, message.globalId, message.messageId)
          updateStatusAndNotify(SendingStatus.FAILED)
        case RETRYABLE_ERROR =>
          if (retryDurationExpired) {
            logRetryingTimedOut(httpStatus, globalId, messageId)
            updateStatusAndNotify(SendingStatus.FAILED)
          } else {
            logContinuingRetrying(httpStatus, globalId, messageId)
            outboundMessageRepository.updateNextRetryTime(message.globalId, nextRetryInstant).map(_ => ())
          }
        case _ => Future.unit
      }
    }
  }

  private def processSendingResult(message: MessageRequest, soapRequest: SoapRequest, httpStatus: Int): OutboundSoapMessage = {
    val globalId: UUID = randomUUID
    val messageId = message.addressing.messageId

    def succeededMessage = {
      SentOutboundSoapMessage(globalId, messageId, soapRequest.soapEnvelope, soapRequest.destinationUrl, now,
        httpStatus, message.notificationUrl, None, None, Some(now))
    }

    def failedMessage = {
      FailedOutboundSoapMessage(globalId, messageId, soapRequest.soapEnvelope, soapRequest.destinationUrl, now, httpStatus, message.notificationUrl)
    }

    def retryingMessage = {
      RetryingOutboundSoapMessage(globalId, messageId, soapRequest.soapEnvelope, soapRequest.destinationUrl, now,
        now.plus(Duration.ofMillis(appConfig.retryInterval.toMillis)), httpStatus, message.notificationUrl, None, None)
    }

    mapHttpStatusCode(httpStatus) match {
      case SUCCESS => succeededMessage
      case UNEXPECTED_SUCCESS => succeededMessage
      case FAIL_ERROR => failedMessage
      case RETRYABLE_ERROR => retryingMessage
    }
  }

  private def buildSoapRequest(message: MessageRequest): Future[SoapRequest] = {
    cache.getOrElseUpdate[Definition](message.wsdlUrl, appConfig.cacheDuration) {
      parseWsdl(message.wsdlUrl)
    } map { wsdlDefinition: Definition =>
      val portType = wsdlDefinition.getAllPortTypes.asScala.values.head.asInstanceOf[PortType]
      val operation: Operation = portType.getOperations.asScala.map(_.asInstanceOf[Operation])
        .find(_.getName == message.wsdlOperation).getOrElse(throw new NotFoundException(s"Operation ${message.wsdlOperation} not found"))

      val envelope: SOAPEnvelope = getSOAP12Factory.getDefaultEnvelope
      addHeaders(message, operation, envelope)
      addBody(message, operation, envelope)
      val enrichedEnvelope: String = if (appConfig.enableMessageSigning) wsSecurityService.addSignature(envelope)
                                     else wsSecurityService.addUsernameToken(envelope)
      val url: String = wsdlDefinition.getAllServices.asScala.values.head.asInstanceOf[Service]
        .getPorts.asScala.values.head.asInstanceOf[Port]
        .getExtensibilityElements.asScala.filter(_.isInstanceOf[SOAP12Address]).head.asInstanceOf[SOAP12Address]
        .getLocationURI
      val soapWsdlUrl: String = url.replace("{ccn2Host}", appConfig.ccn2Host).replace("{ccn2Port}", appConfig.ccn2Port.toString)
      SoapRequest(enrichedEnvelope, soapWsdlUrl)
    }
  }

  private def parseWsdl(wsdlUrl: String): Future[Definition] = {
    val reader: WSDLReader = WSDLUtil.newWSDLReaderWithPopulatedExtensionRegistry
    reader.setFeature("javax.wsdl.importDocuments", true)
    Future.successful(reader.readWSDL(wsdlUrl))
  }

  private def addHeaders(message: MessageRequest, operation: Operation, envelope: SOAPEnvelope): Unit = {
    val wsaNs: OMNamespace = envelope.declareNamespace(WSA_NAMESPACE, "wsa")
    addSoapAction(operation, wsaNs, envelope)
    addMessageHeader(message, operation, envelope)
    addOptionalAddressingHeaders(message, wsaNs, envelope)
  }

  private def addMessageHeader(message: MessageRequest, operation: Operation, envelope: SOAPEnvelope): Unit = {
    val ccnmNamespace: OMNamespace = getOMFactory.createOMNamespace("http://ccn2.ec.eu/CCN2.Service.Platform.Common.Schema", "ccnm")
    val messageHeader: OMElement = getOMFactory.createOMElement("MessageHeader", ccnmNamespace)
    envelope.getHeader.addChild(messageHeader)

    def addToMessageHeader(elementName: String, elementContent: String): Unit = {
      val element = getOMFactory.createOMElement(elementName, ccnmNamespace)
      getOMFactory.createOMText(element, elementContent)
      messageHeader.addChild(element)
    }

    addToMessageHeader("Version", "1.0")
    addToMessageHeader("SendingDateAndTime", Instant.now.toString)
    findSoapAction(operation).foreach(addToMessageHeader("MessageType", _))
    addToMessageHeader("RequestCoD", message.confirmationOfDelivery.getOrElse(false).toString)
  }

  private def findSoapAction(operation: Operation): Option[String] = {
    operation.getInput.getExtensionAttribute(new QName(WSAW_NAMESPACE, "action")) match {
      case qName: QName => Some(qName.getLocalPart)
      case _ => None
    }
  }

  private def addSoapAction(operation: Operation, wsaNs: OMNamespace, envelope: SOAPEnvelope): Unit = {
    findSoapAction(operation).foreach(addToSoapHeader(_, WSA_ACTION, wsaNs, envelope))
  }

  private def addOptionalAddressingHeaders(message: MessageRequest, wsaNs: OMNamespace, envelope: SOAPEnvelope): Unit = {
    addWithAddressElementChildToSoapHeader(_ => true , message.addressing.from, WSA_FROM, wsaNs, envelope)
    addToSoapHeader(message.addressing.to, WSA_TO, wsaNs, envelope)
    addWithAddressElementChildToSoapHeader(x => x.trim != "", message.addressing.replyTo, WSA_REPLY_TO, wsaNs, envelope)
    addWithAddressElementChildToSoapHeader(x => x.trim != "", message.addressing.faultTo, WSA_FAULT_TO, wsaNs, envelope)
    addToSoapHeader(message.addressing.messageId, WSA_MESSAGE_ID, wsaNs, envelope)
    message.addressing.relatesTo.foreach(addToSoapHeader(_, WSA_RELATES_TO, wsaNs, envelope))
  }

  private def addToSoapHeader(property: String, elementName: String, namespace: OMNamespace, envelope: SOAPEnvelope): Unit = {
    val addressingElement = getOMFactory.createOMElement(elementName, namespace)
    getOMFactory.createOMText(addressingElement, property)
    envelope.getHeader.addChild(addressingElement)
  }

  private def addWithAddressElementChildToSoapHeader(isRequired: String => Boolean, property: String, elementName: String,
                                                     namespace: OMNamespace, envelope: SOAPEnvelope): Unit = {
    if (isRequired(property)) {
      val addressingElement: OMElement = getOMFactory.createOMElement(elementName, namespace)
      val addressingElementInner: OMElement = getOMFactory.createOMElement("Address", namespace)
      getOMFactory.createOMText(addressingElementInner, property)
      addressingElement.addChild(addressingElementInner)
      envelope.getHeader.addChild(addressingElement)
    }
  }

  private def addBody(message: MessageRequest, operation: Operation, envelope: SOAPEnvelope): Unit = {
    val inputMessageName = operation.getInput.getMessage.getParts.asScala.values.head.asInstanceOf[Part].getElementName
    val payload = getOMFactory.createOMElement(inputMessageName)
    if (message.messageBody.nonEmpty) payload.addChild(stringToOM(message.messageBody))
    envelope.getBody.addChild(payload)
  }

  private def mapHttpStatusCode(httpStatusCode: Int): CcnRequestResult.Value = {
    if (isSuccessful(httpStatusCode)){
      httpStatusCode match {
        case ACCEPTED => SUCCESS
        case _ => UNEXPECTED_SUCCESS
      }
    } else if (isInformational(httpStatusCode) || isRedirect(httpStatusCode) || isClientError(httpStatusCode)) {
      FAIL_ERROR
    } else {
      RETRYABLE_ERROR
    }
  }

  private def logSendingFailure(httpStatus: Int, globalId: UUID, messageId: String): Unit = {
    logger.error(s"Message with global ID $globalId message ID and $messageId, got status code $httpStatus and failed")
  }

  private def logRetryingTimedOut(httpStatus: Int, globalId: UUID, messageId: String): Unit = {
    logger.error(s"Retried message with global ID $globalId and message ID $messageId, got status code $httpStatus and failed on last attempt")
  }

  private def logContinuingRetrying(httpStatus: Int, globalId: UUID, messageId: String): Unit = {
    logger.warn(s"Retried message with global ID $globalId and message ID $messageId, got status code $httpStatus and will retry")
  }

  private def logSuccess(httpStatus: Int, globalId: UUID, messageId: String): Unit = {
    val successLogMsg: String = s"Message with global ID $globalId and message ID $messageId got status code $httpStatus and successfully sent"
    httpStatus match {
      case ACCEPTED => logger.info(successLogMsg)
      case _ => logger.warn(successLogMsg)
    }
  }

  private def logCcnSendResult(osm: OutboundSoapMessage, httpStatus: Int): Future[Unit] = {
    val globalId = osm.globalId
    val messageId = osm.messageId
    mapHttpStatusCode(httpStatus) match {
      case SUCCESS => logSuccess(httpStatus, globalId, messageId)
      case UNEXPECTED_SUCCESS => logSuccess(httpStatus, globalId, messageId)
      case FAIL_ERROR => logSendingFailure(httpStatus, globalId, messageId)
      case RETRYABLE_ERROR => logContinuingRetrying(httpStatus, globalId, messageId)
    }
    Future.unit
  }
}
