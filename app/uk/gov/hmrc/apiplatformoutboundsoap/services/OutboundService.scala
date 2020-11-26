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
import org.apache.axiom.om.OMAbstractFactory.{getOMFactory, getSOAP12Factory}
import org.apache.axiom.om._
import org.apache.axiom.om.util.AXIOMUtil.stringToOM
import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.wsdl.WSDLUtil
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.MessageRequest
import uk.gov.hmrc.http.NotFoundException

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundService @Inject()(outboundConnector: OutboundConnector)(implicit ec: ExecutionContext) {

  val logger: LoggerLike = Logger

  def sendMessage(message: MessageRequest): Future[Int] = {
    val envelope: SOAPEnvelope = getSOAP12Factory.getDefaultEnvelope
    envelope.getBody.addChild(buildPayload(message))
    outboundConnector.postMessage(envelope.toString)
  }

  private def buildPayload(message: MessageRequest): OMElement = {
    val wsdlDefinition: Definition = parseWsdl(message.wsdlUrl)
    val portType = wsdlDefinition.getAllPortTypes.asScala.values.head.asInstanceOf[PortType]
    val operation: Operation = portType.getOperations.asScala.map(_.asInstanceOf[Operation])
      .find(_.getName == message.wsdlOperation).getOrElse(throw new NotFoundException(s"Operation ${message.wsdlOperation} not found"))
    val inputMessageName = operation.getInput.getMessage.getParts.asScala.values.head.asInstanceOf[Part].getElementName

    val payload = getOMFactory.createOMElement(inputMessageName)
    payload.addChild(stringToOM(message.messageBody))
    payload
  }

  private def parseWsdl(wsdlUrl: String): Definition = {
    val reader: WSDLReader = WSDLUtil.newWSDLReaderWithPopulatedExtensionRegistry
    reader.setFeature("javax.wsdl.importDocuments", true)
    reader.readWSDL(wsdlUrl)
  }
}
