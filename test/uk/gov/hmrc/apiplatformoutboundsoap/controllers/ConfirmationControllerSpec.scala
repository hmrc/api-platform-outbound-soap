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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import akka.stream.Materializer
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders.ValidateConfirmationTypeAction
import uk.gov.hmrc.apiplatformoutboundsoap.models.DeliveryStatus
import uk.gov.hmrc.apiplatformoutboundsoap.models.common.{MessageIdNotFoundResult, UpdateSuccessResult}
import uk.gov.hmrc.apiplatformoutboundsoap.services.ConfirmationService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.{Elem, NodeSeq, XML}

class ConfirmationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    val confirmationServiceMock: ConfirmationService = mock[ConfirmationService]
    private val validateConfirmationTypeAction       = app.injector.instanceOf[ValidateConfirmationTypeAction]
    val underTest                                    = new ConfirmationController(Helpers.stubControllerComponents(), confirmationServiceMock, validateConfirmationTypeAction)
  }

  "message" should {
    val fakeRequest                         = FakeRequest("POST", "/acknowledgement")
    def createCodMessage(relatesTo: String) = {
      """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
        |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoD</wsa:Action>
        |        <wsa:From>
        |            <wsa:Address>[FROM]</wsa:Address>
        |        </wsa:From>
        |        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/ack">[RELATES_TO]</wsa:RelatesTo>
        |        <wsa:MessageID>[COD_MESSAGE_ID]</wsa:MessageID>
        |        <wsa:To>[TO]</wsa:To>
        |    </soap:Header>
        |    <soap:Body>
        |        <ccn2:CoD>
        |            <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
        |        </ccn2:CoD>
        |    </soap:Body>
        |</soap:Envelope>""".stripMargin.replaceAll("\n", "")
        .replaceAll("\\[RELATES_TO]", relatesTo)
    }

    val codMessageWithNoRelatesTo =
      """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
        |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoD</wsa:Action>
        |        <wsa:From>
        |            <wsa:Address>[FROM]</wsa:Address>
        |        </wsa:From>
        |        <wsa:MessageID>[COD_MESSAGE_ID]</wsa:MessageID>
        |        <wsa:To>[TO]</wsa:To>
        |    </soap:Header>
        |    <soap:Body>
        |        <ccn2:CoD>
        |            <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
        |        </ccn2:CoD>
        |    </soap:Body>
        |</soap:Envelope>""".stripMargin.replaceAll("\n", "")

    val coeMessage       =
      """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
        |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoE</wsa:Action>
        |        <wsa:From>
        |            <wsa:Address>[FROM]</wsa:Address>
        |        </wsa:From>
        |        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/err">zyxw9876</wsa:RelatesTo>
        |        <wsa:MessageID>[COE_MESSAGE_ID]</wsa:MessageID>
        |        <wsa:To>[TO]</wsa:To>
        |    </soap:Header>
        |    <soap:Body>
        |      <ccn2:CoE>
        |         <ccn2:Severity>?</ccn2:Severity>
        |         <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
        |         <!--Optional:-->
        |         <ccn2:Payload>
        |            <soap:Fault>
        |               <soap:Code>
        |                  <soap:Value>?</soap:Value>
        |               </soap:Code>
        |               <soap:Reason>
        |                  <soap:Text xml:lang="?">?</soap:Text>
        |               </soap:Reason>
        |               <soap:Node>?</soap:Node>
        |               <soap:Role>?</soap:Role>
        |               <soap:Detail>
        |               </soap:Detail>
        |            </soap:Fault>
        |            <ccn2:Message>?</ccn2:Message>
        |         </ccn2:Payload>
        |      </ccn2:CoE>
        |   </soap:Body>
        |</soap:Envelope>""".stripMargin.replaceAll("\n", "")
    val msgIdCod: String = "1234abcd"
    val msgIdCoe: String = "zyxw9876"

    "call the confirmation service with a CoD request" in new Setup {
      val confirmationXmlRequestCaptor = ArgumentCaptor.forClass(classOf[NodeSeq])
      val msgIdCaptor                  = ArgumentCaptor.forClass(classOf[String])
      val confirmationTypeCaptor       = ArgumentCaptor.forClass(classOf[DeliveryStatus])
      val requestBodyXml: Elem         = XML.loadString(createCodMessage("1234abcd"))
      when(confirmationServiceMock.processConfirmation(confirmationXmlRequestCaptor.capture, msgIdCaptor.capture(), confirmationTypeCaptor.capture)(*))
        .thenReturn(Future.successful(UpdateSuccessResult))
      val result: Future[Result]       = underTest.message()(fakeRequest.withBody(requestBodyXml)
        .withHeaders("ContentType" -> "text/xml", "x-soap-action" -> "CCN2.Service.Platform.AcknowledgementService/CoD"))
      status(result) shouldBe ACCEPTED
      confirmationXmlRequestCaptor.getValue shouldBe requestBodyXml
      msgIdCaptor.getValue shouldBe msgIdCod
      confirmationTypeCaptor.getValue shouldBe DeliveryStatus.COD
      verify(confirmationServiceMock).processConfirmation(refEq(requestBodyXml), refEq(msgIdCod), refEq(DeliveryStatus.COD))(*)

    }

    "handle an XML request with no RelatesTo element" in new Setup {
      val requestBodyXml: Elem   = XML.loadString(codMessageWithNoRelatesTo)
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(requestBodyXml)
        .withHeaders("ContentType" -> "text/xml", "x-soap-action" -> "CCN2.Service.Platform.AcknowledgementService/CoD"))
      status(result) shouldBe BAD_REQUEST
      verifyZeroInteractions(confirmationServiceMock)
    }

    "return 400 when exists an XML request with blank, whitespaces, newline and tab space in RelatesTo element" in new Setup {
      Seq("", "  ", "\n", "\t") foreach { relatesTo =>
        val requestBodyXml: Elem   = XML.loadString(createCodMessage(relatesTo))
        val result: Future[Result] = underTest.message()(fakeRequest.withBody(requestBodyXml)
          .withHeaders("ContentType" -> "text/xml", "x-soap-action" -> "CCN2.Service.Platform.AcknowledgementService/CoD"))
        status(result) shouldBe BAD_REQUEST
        verifyZeroInteractions(confirmationServiceMock)
      }
    }

    "call the confirmation service with a CoE request" in new Setup {
      val confirmationXmlRequestCaptor = ArgumentCaptor.forClass(classOf[NodeSeq])
      val msgIdCaptor                  = ArgumentCaptor.forClass(classOf[String])
      val confirmationTypeCaptor       = ArgumentCaptor.forClass(classOf[DeliveryStatus])
      val requestBodyXml: Elem         = XML.loadString(coeMessage)
      when(confirmationServiceMock.processConfirmation(confirmationXmlRequestCaptor.capture, msgIdCaptor.capture(), confirmationTypeCaptor.capture)(*))
        .thenReturn(Future.successful(UpdateSuccessResult))
      val result: Future[Result]       = underTest.message()(fakeRequest.withBody(requestBodyXml)
        .withHeaders("ContentType" -> "text/xml", "x-soap-action" -> "CCN2.Service.Platform.AcknowledgementService/CoE"))
      status(result) shouldBe ACCEPTED
      confirmationXmlRequestCaptor.getValue shouldBe requestBodyXml
      msgIdCaptor.getValue shouldBe msgIdCoe
      confirmationTypeCaptor.getValue shouldBe DeliveryStatus.COE
      verify(confirmationServiceMock).processConfirmation(refEq(requestBodyXml), refEq(msgIdCoe), refEq(DeliveryStatus.COE))(*)
    }

    "call the confirmation service with RelatesTo id that is unknown" in new Setup {
      val requestBodyXml: Elem   = XML.loadString(coeMessage)
      when(confirmationServiceMock.processConfirmation(*, *, *)(*))
        .thenReturn(Future.successful(MessageIdNotFoundResult))
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(requestBodyXml)
        .withHeaders("ContentType" -> "text/xml", "x-soap-action" -> "CCN2.Service.Platform.AcknowledgementService/CoE"))
      status(result) shouldBe NOT_FOUND
    }

    "handle receiving a request with an invalid SOAP action" in new Setup {
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(XML.loadString(createCodMessage("1234abcd")))
        .withHeaders("ContentType" -> "text/xml", "x-soap-action" -> "foobar"))
      status(result) shouldBe BAD_REQUEST
      verifyZeroInteractions(confirmationServiceMock)
    }

    "handle receiving a request with no x-soap-action header" in new Setup {
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(XML.loadString(createCodMessage("1234abcd")))
        .withHeaders("ContentType" -> "text/xml"))
      status(result) shouldBe BAD_REQUEST
      verifyZeroInteractions(confirmationServiceMock)
    }
  }
}
