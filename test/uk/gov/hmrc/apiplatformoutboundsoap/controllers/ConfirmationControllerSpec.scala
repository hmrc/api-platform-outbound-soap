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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import akka.stream.Materializer
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformoutboundsoap.models.DeliveryStatus
import uk.gov.hmrc.apiplatformoutboundsoap.services.ConfirmationService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConfirmationControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    val confirmationServiceMock: ConfirmationService = mock[ConfirmationService]
    val underTest = new ConfirmationController(Helpers.stubControllerComponents(), confirmationServiceMock)
  }

  "message" should {
    val fakeRequest = FakeRequest("POST", "/acknowledgement")
    val codMessage =
      """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
        |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoD</wsa:Action>
        |        <wsa:From>
        |            <wsa:Address>[FROM]</wsa:Address>
        |        </wsa:From>
        |        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/ack">[ORIGINAL_MESSAGE_ID]</wsa:RelatesTo>
        |        <wsa:MessageID>[COD_MESSAGE_ID]</wsa:MessageID>
        |        <wsa:To>[TO]</wsa:To>
        |    </soap:Header>
        |    <soap:Body>
        |        <ccn2:CoD>
        |            <ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp>
        |        </ccn2:CoD>
        |    </soap:Body>
        |</soap:Envelope>""".stripMargin.replaceAll("\n", "")

    val coeMessage =
      """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:ccn2="http://ccn2.ec.eu/CCN2.Service.Platform.Acknowledgement.Schema">
        |    <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
        |        <wsa:Action>CCN2.Service.Platform.AcknowledgementService/CoE</wsa:Action>
        |        <wsa:From>
        |            <wsa:Address>[FROM]</wsa:Address>
        |        </wsa:From>
        |        <wsa:RelatesTo RelationshipType="http://ccn2.ec.eu/addressing/err">[ORIGINAL_MESSAGE_ID]</wsa:RelatesTo>
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
        |    </soap:Body>
        |</soap:Envelope>""".stripMargin.replaceAll("\n", "")

    "call the confirmation service with a CoD request" in new Setup {
      val confirmationRequestCaptor = ArgumentCaptor.forClass(classOf[Option[String]])
      val confirmationTypeCaptor = ArgumentCaptor.forClass(classOf[DeliveryStatus])
      doNothing.when(confirmationServiceMock).processConfirmation(confirmationRequestCaptor.capture, confirmationTypeCaptor.capture)(*)
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(codMessage)
        .withHeaders("ContentType" -> "text/plain", "x-soap-action" -> "cod"))
      status(result) shouldBe OK
      confirmationRequestCaptor.getValue shouldBe Some(codMessage)
      confirmationTypeCaptor.getValue shouldBe DeliveryStatus.COD
    }

    "call the confirmation service with a CoE request" in new Setup {
      val confirmationRequestCaptor = ArgumentCaptor.forClass(classOf[Option[String]])
      val confirmationTypeCaptor = ArgumentCaptor.forClass(classOf[DeliveryStatus])
      doNothing.when(confirmationServiceMock).processConfirmation(confirmationRequestCaptor.capture, confirmationTypeCaptor.capture)(*)
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(coeMessage)
        .withHeaders("ContentType" -> "text/plain", "x-soap-action" -> "coe"))
      status(result) shouldBe OK
      confirmationRequestCaptor.getValue shouldBe Some(coeMessage)
      confirmationTypeCaptor.getValue shouldBe DeliveryStatus.COE
    }

    "handle receiving a request with an invalid SOAP action" in new Setup {
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(coeMessage)
        .withHeaders("ContentType" -> "text/plain", "x-soap-action" -> "foobar"))
      status(result) shouldBe BAD_REQUEST
      verifyZeroInteractions(confirmationServiceMock)
    }
  }
}