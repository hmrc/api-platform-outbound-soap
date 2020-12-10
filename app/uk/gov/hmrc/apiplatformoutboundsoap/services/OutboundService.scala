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

import javax.inject.{Inject, Singleton}
import javax.wsdl.xml.WSDLReader
import javax.wsdl.{Definition, Operation, Part, PortType}
import javax.xml.namespace.QName
import org.apache.axiom.om.OMAbstractFactory.{getOMFactory, getSOAP12Factory}
import org.apache.axiom.om._
import org.apache.axiom.om.util.AXIOMUtil.stringToOM
import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.addressing.AddressingConstants.Final.{WSAW_NAMESPACE, WSA_NAMESPACE}
import org.apache.axis2.addressing.AddressingConstants._
import org.apache.axis2.wsdl.WSDLUtil
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.MessageRequest
import uk.gov.hmrc.http.NotFoundException

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundService @Inject()(outboundConnector: OutboundConnector, wsSecurityService: WsSecurityService)
                               (implicit ec: ExecutionContext) {
  val logger: LoggerLike = Logger

  def sendMessage(message: MessageRequest): Future[Int] = {
    val envelope = buildEnvelope(message)
    val envelopeWithCredentials = wsSecurityService.addUsernameToken(envelope)
    outboundConnector.postMessage(envelopeWithCredentials)
  }

  private def buildEnvelope(message: MessageRequest): SOAPEnvelope = {
    val wsdlDefinition: Definition = parseWsdl(message.wsdlUrl)
    val portType = wsdlDefinition.getAllPortTypes.asScala.values.head.asInstanceOf[PortType]
    val operation: Operation = portType.getOperations.asScala.map(_.asInstanceOf[Operation])
      .find(_.getName == message.wsdlOperation).getOrElse(throw new NotFoundException(s"Operation ${message.wsdlOperation} not found"))

    val envelope: SOAPEnvelope = getSOAP12Factory.getDefaultEnvelope
    addHeaders(message, operation, envelope)
    addBody(message, operation, envelope)
    envelope
  }

  private def parseWsdl(wsdlUrl: String): Definition = {
    val reader: WSDLReader = WSDLUtil.newWSDLReaderWithPopulatedExtensionRegistry
    reader.setFeature("javax.wsdl.importDocuments", true)
    reader.readWSDL(wsdlUrl)
  }

  private def addHeaders(message: MessageRequest, operation: Operation, envelope: SOAPEnvelope): Unit = {
    val wsaNs: OMNamespace = getOMFactory.createOMNamespace(WSA_NAMESPACE, "wsa")
    addSoapAction(operation, wsaNs, envelope)
    addOptionalAddressingHeaders(message, wsaNs, envelope)
  }

  private def addSoapAction(operation: Operation, wsaNs: OMNamespace, envelope: SOAPEnvelope): Unit = {
    (operation.getInput.getExtensionAttribute(new QName(WSAW_NAMESPACE, "action")) match {
      case qName: QName => Some(qName.getLocalPart)
      case _ => None
    }).foreach(addToSoapHeader(_, WSA_ACTION, wsaNs, envelope))
  }

  private def addOptionalAddressingHeaders(message: MessageRequest, wsaNs: OMNamespace, envelope: SOAPEnvelope): Unit = {
    message.addressing foreach { addressing =>
      addressing.from.foreach(addToSoapHeader(_, WSA_FROM, wsaNs, envelope))
      addressing.to.foreach(addToSoapHeader(_, WSA_TO, wsaNs, envelope))
      addressing.replyTo.foreach(addToSoapHeader(_, WSA_REPLY_TO, wsaNs, envelope))
      addressing.faultTo.foreach(addToSoapHeader(_, WSA_FAULT_TO, wsaNs, envelope))
      addressing.messageId.foreach(addToSoapHeader(_, WSA_MESSAGE_ID, wsaNs, envelope))
      addressing.relatesTo.foreach(addToSoapHeader(_, WSA_RELATES_TO, wsaNs, envelope))
    }
  }

  private def addToSoapHeader(property: String, elementName: String, namespace: OMNamespace, envelope: SOAPEnvelope): Unit = {
    val addressingElement = getOMFactory.createOMElement(elementName, namespace)
    getOMFactory.createOMText(addressingElement, property)
    envelope.getHeader.addChild(addressingElement)
  }

  private def addBody(message: MessageRequest, operation: Operation, envelope: SOAPEnvelope): Unit = {
    val inputMessageName = operation.getInput.getMessage.getParts.asScala.values.head.asInstanceOf[Part].getElementName
    val payload = getOMFactory.createOMElement(inputMessageName)
    payload.addChild(stringToOM(message.messageBody))
    envelope.getBody.addChild(payload)
  }
}
