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

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.apache.axiom.om.OMAbstractFactory.{getOMFactory, getSOAP12Factory}
import org.apache.axiom.om._
import org.apache.axiom.om.util.AXIOMUtil.stringToOM
import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.addressing.AddressingConstants.Final.{WSAW_NAMESPACE, WSA_NAMESPACE}
import org.apache.axis2.addressing.AddressingConstants._
import org.apache.axis2.wsdl.WSDLUtil
import org.apache.http.HttpStatus
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import play.api.cache.AsyncCacheApi
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.{NotificationCallbackConnector, OutboundConnector}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, NotFoundException}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import javax.wsdl.extensions.soap12.SOAP12Address
import javax.wsdl.xml.WSDLReader
import javax.wsdl.{Definition, Operation, Part, Port, PortType, Service}
import javax.xml.namespace.QName
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundService @Inject()(outboundConnector: OutboundConnector,
                                wsSecurityService: WsSecurityService,
                                outboundMessageRepository: OutboundMessageRepository,
                                notificationCallbackConnector: NotificationCallbackConnector,
                                appConfig: AppConfig,
                                cache: AsyncCacheApi)
                               (implicit val ec: ExecutionContext, mat: Materializer)
  extends HttpErrorFunctions {
  val logger: LoggerLike = Logger
  val dateTimeFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime()

  def now: DateTime = DateTime.now(UTC)

  def randomUUID: UUID = UUID.randomUUID

  def sendMessage(message: MessageRequest): Future[OutboundSoapMessage] = {
    for {
      soapRequest <- buildSoapRequest(message)
      result <- outboundConnector.postMessage(message.addressing.messageId, soapRequest)
      outboundSoapMessage = buildOutboundSoapMessage(message, soapRequest, result)
      _ <- outboundMessageRepository.persist(outboundSoapMessage)
    } yield outboundSoapMessage
  }

  def retryMessages(implicit hc: HeaderCarrier): Future[Done] = {
    outboundMessageRepository.retrieveMessagesForRetry.runWith(Sink.foreachAsync[RetryingOutboundSoapMessage](appConfig.parallelism)(retryMessage))
  }

  private def retryMessage(message: RetryingOutboundSoapMessage)(implicit hc: HeaderCarrier): Future[Unit] = {
    val nextRetryDateTime: DateTime = now.plus(appConfig.retryInterval.toMillis)
    val globalId = message.globalId
    val messageId = message.messageId

    outboundConnector.postMessage(messageId, SoapRequest(message.soapMessage, message.destinationUrl)) flatMap { result =>
      if (is2xx(result)) {
        log2xxResult(result, globalId, messageId)
        outboundMessageRepository.updateSendingStatus(message.globalId, SendingStatus.SENT) map { updatedMessage =>
          updatedMessage.map(notificationCallbackConnector.sendNotification)
          ()
        }
      } else {
        if (message.createDateTime.plus(appConfig.retryDuration.toMillis).isBefore(now.getMillis)) {
          logger.error(s"Retried message with global ID ${message.globalId}  message ID ${message.messageId} got status code $result " +
            s"and failed on last attempt")
          outboundMessageRepository.updateSendingStatus(message.globalId, SendingStatus.FAILED).map { updatedMessage =>
            updatedMessage.map(notificationCallbackConnector.sendNotification)
            ()
          }
        } else {
          logger.warn(s"Retried message with global ID ${message.globalId} message ID ${message.messageId} got status code $result and will retry")
          outboundMessageRepository.updateNextRetryTime(message.globalId, nextRetryDateTime).map(_ => ())
        }
      }
    }
  }

  private def buildOutboundSoapMessage(message: MessageRequest, soapRequest: SoapRequest, result: Int): OutboundSoapMessage = {
    val globalId: UUID = randomUUID
    val messageId = message.addressing.messageId
    def is3xx(result: Int): Boolean = result >= 300 && result < 400

    if (is2xx(result)) {
      log2xxResult(result, globalId, messageId)
      SentOutboundSoapMessage(globalId, messageId, soapRequest.soapEnvelope, soapRequest.destinationUrl, now, result, message.notificationUrl)
    } else if(is3xx(result)|| is4xx(result)) {
      logger.error(s"Message with global ID $globalId message ID $messageId got status code $result and failed")
      FailedOutboundSoapMessage(globalId, messageId, soapRequest.soapEnvelope, soapRequest.destinationUrl, now, result, message.notificationUrl)
    } else {
      logger.warn(s"Message with global ID $globalId message ID $messageId got status code $result and will retry")
      RetryingOutboundSoapMessage(globalId, messageId, soapRequest.soapEnvelope, soapRequest.destinationUrl, now,
        now.plus(appConfig.retryInterval.toMillis), result, message.notificationUrl, None, None)
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
    addToMessageHeader("SendingDateAndTime", DateTime.now().toString(dateTimeFormatter))
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
    if(isRequired(property)) {
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


  private def log2xxResult(result: Int, globalId: UUID, messageId: String) = {
    if (result != HttpStatus.SC_ACCEPTED) {
      logger.warn(s"Message with global ID $globalId message ID $messageId got status code $result and successfully sent")
    } else {
      logger.info(s"Message with global ID $globalId message ID $messageId got status code $result and successfully sent")
    }
  }
}
