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

import java.time.Instant
import java.util.UUID
import javax.wsdl.WSDLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

import akka.stream.Materializer
import org.mockito.captor.{ArgCaptor, Captor}
import org.mockito.scalatest.ResetMocksAfterEachTest
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{JsBoolean, Json}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.NotFoundException

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.{addressingFormatter, privateHeaderFormatter}
import uk.gov.hmrc.apiplatformoutboundsoap.models.{Addressing, MessageRequest, PrivateHeader, SendingStatus, SentOutboundSoapMessage}
import uk.gov.hmrc.apiplatformoutboundsoap.services.OutboundService

class OutboundControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite
    with ArgumentMatchersSugar with ResetMocksAfterEachTest {
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    val mockAppConfig                        = mock[AppConfig]
    when(mockAppConfig.confirmationOfDelivery).thenReturn(true)
    val outboundServiceMock: OutboundService = mock[OutboundService]
    val underTest                            = new OutboundController(Helpers.stubControllerComponents(), mockAppConfig, outboundServiceMock)
  }

  "message" should {
    val fakeRequest                        = FakeRequest("POST", "/message")
    val addressing                         = Addressing(messageId = "987", to = "AddressedTo", replyTo = "ReplyTo", faultTo = "FaultTo", from = "from")
    val addressingJson                     = Json.toJson(addressing)
    val privateHeaders                     = List(PrivateHeader(name = "name1", value = Some("value1")), PrivateHeader(name = "name2", value = Some("value2")))
    val privateHeadersEmptyValue           = List(PrivateHeader(name = "name1", value = None), PrivateHeader(name = "name2", value = Some("value2")))
    val privateHeadersTooMany              = privateHeaders ++ List(
      PrivateHeader(name = "name3", value = Some("value3")),
      PrivateHeader(name = "name4", value = Some("value4")),
      PrivateHeader(name = "name5", value = Some("value5")),
      PrivateHeader(name = "name6", value = Some("value6"))
    )
    val privateHeadersJson                 = Json.toJson(privateHeaders)
    val privateHeadersTooManyJson          = Json.toJson(privateHeadersTooMany)
    val message                            = Json.obj("wsdlUrl" -> "http://example.com/wsdl", "wsdlOperation" -> "theOp", "messageBody" -> "<IE4N03>example</IE4N03>", "addressing" -> addressingJson)
    val messageWithPrivateHeaders          = Json.obj(
      "wsdlUrl"        -> "http://example.com/wsdl",
      "wsdlOperation"  -> "theOp",
      "messageBody"    -> "<IE4N03>example</IE4N03>",
      "addressing"     -> addressingJson,
      "privateHeaders" -> privateHeadersJson
    )
    val messageWithTooManyPrivateHeaders   = Json.obj(
      "wsdlUrl"        -> "http://example.com/wsdl",
      "wsdlOperation"  -> "theOp",
      "messageBody"    -> "<IE4N03>example</IE4N03>",
      "addressing"     -> addressingJson,
      "privateHeaders" -> privateHeadersTooManyJson
    )
    val messageWithEmptyValuePrivateHeader = Json.obj(
      "wsdlUrl"        -> "http://example.com/wsdl",
      "wsdlOperation"  -> "theOp",
      "messageBody"    -> "<IE4N03>example</IE4N03>",
      "addressing"     -> addressingJson,
      "privateHeaders" -> privateHeadersEmptyValue
    )

    val outboundSoapMessage = SentOutboundSoapMessage(UUID.randomUUID, "123", "envelope", "some url", Instant.now, OK)

    "return the response returned by the outbound service" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(message))

      status(result) shouldBe OK
      (contentAsJson(result) \ "globalId").as[UUID] shouldBe outboundSoapMessage.globalId
      (contentAsJson(result) \ "messageId").as[String] shouldBe outboundSoapMessage.messageId
      (contentAsJson(result) \ "status").as[SendingStatus] shouldBe outboundSoapMessage.status
    }

    "send the message request to the outbound service" in new Setup {
      val messageCaptor: Captor[MessageRequest] = ArgCaptor[MessageRequest]
      when(outboundServiceMock.sendMessage(messageCaptor)).thenReturn(successful(outboundSoapMessage))

      underTest.message()(fakeRequest.withBody(message))

      verify(outboundServiceMock).sendMessage(any)
      messageCaptor hasCaptured MessageRequest("http://example.com/wsdl", "theOp", "<IE4N03>example</IE4N03>", addressing, Some(true))
    }

    "send the message request with private headers to the outbound service" in new Setup {
      val messageCaptor: Captor[MessageRequest] = ArgCaptor[MessageRequest]
      when(outboundServiceMock.sendMessage(messageCaptor)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(messageWithPrivateHeaders))
      status(result) shouldBe OK
      verify(outboundServiceMock).sendMessage(any)
      messageCaptor hasCaptured MessageRequest("http://example.com/wsdl", "theOp", "<IE4N03>example</IE4N03>", addressing, Some(true), None, Some(privateHeaders))
    }

    "return Bad Request when too many private headers are sent in the message request" in new Setup {
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(messageWithTooManyPrivateHeaders))
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "statusCode").as[Int] shouldBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] shouldBe "Maximum 5 private headers are allowed in message request"
      verifyZeroInteractions(outboundServiceMock)
    }

    "return OK response when the a private header has an empty value" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(messageWithEmptyValuePrivateHeader))

      status(result) shouldBe OK
    }

    "return bad response when the request json private header has an invalid name key" in new Setup {
      val result: Future[Result] = underTest.message()(fakeRequest.withBody(
        Json.obj(
          "wsdlUrl"        -> "http://example.com/wsdl",
          "wsdlOperation"  -> "theOp",
          "messageBody"    -> "<IE4N03>example</IE4N03>",
          "addressing"     ->
            Json.obj("messageId" -> "some msg id", "to" -> "who it is to", "from" -> "from"),
          "privateHeaders" -> Json.arr(Json.obj("name123" -> "value"))
        )
      ))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid MessageRequest payload: List((/privateHeaders(0)/name,List(JsonValidationError(List(error.path.missing),List()))))"
      verifyZeroInteractions(outboundServiceMock)
    }

    "return OK response with defaults when the request json body addressing section has missing replyTo, faultTo addressing fields" in new Setup {
      val messageCaptor: ArgumentCaptor[MessageRequest] = ArgumentCaptor.forClass(classOf[MessageRequest])
      when(outboundServiceMock.sendMessage(messageCaptor.capture())).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(Json.obj(
        "wsdlUrl"       -> "http://example.com/wsdl",
        "wsdlOperation" -> "theOp",
        "messageBody"   -> "<IE4N03>example</IE4N03>",
        "addressing"    ->
          Json.obj("messageId" -> "some msg id", "to" -> "who it is to", "from" -> "from")
      )))

      status(result) shouldBe OK
      messageCaptor.getValue.addressing.from shouldBe "from"
      messageCaptor.getValue.addressing.replyTo shouldBe ""
      messageCaptor.getValue.addressing.faultTo shouldBe ""
    }

    "return bad request when the request json body addressing section has empty from field" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(Json.obj(
        "wsdlUrl"       -> "http://example.com/wsdl",
        "wsdlOperation" -> "theOp",
        "messageBody"   -> "<IE4N03>example</IE4N03>",
        "addressing"    ->
          Json.obj("messageId" -> "some msg id", "to" -> "who it is to", "from" -> " ")
      )))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe
        "Could not parse body due to addressing.from being empty"
    }

    "return bad request when the request json body is missing wsdlUrl field" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(
        Json.obj("wsdlOperation" -> "theOp", "messageBody" -> "<IE4N03>example</IE4N03>", "addressing" -> Json.toJson(addressing))
      ))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid MessageRequest payload: List((/wsdlUrl,List(JsonValidationError(List(error.path.missing),List()))))"
    }

    "return bad request when the request json body is missing messageBody field" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(
        Json.obj("wsdlUrl" -> "http://example.com/wsdl", "wsdlOperation" -> "theOp", "addressing" -> Json.toJson(addressing))
      ))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid MessageRequest payload: List((/messageBody,List(JsonValidationError(List(error.path.missing),List()))))"
    }

    "return bad request when the request json body addressing section is missing to field" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(Json.obj(
        "wsdlUrl"       -> "http://example.com/wsdl",
        "wsdlOperation" -> "theOp",
        "messageBody"   -> "<IE4N03>example</IE4N03>",
        "addressing"    ->
          Json.obj("messageId" -> "some msg id")
      )))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe
        "Invalid MessageRequest payload: List((/addressing/to,List(JsonValidationError(List(error.path.missing),List()))))"
    }

    "return bad request when the request json body addressing section is missing message ID field" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(Json.obj(
        "wsdlUrl"       -> "http://example.com/wsdl",
        "wsdlOperation" -> "theOp",
        "messageBody"   -> "<IE4N03>example</IE4N03>",
        "addressing"    ->
          Json.obj("to" -> "who it is to")
      )))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe
        "Invalid MessageRequest payload: List((/addressing/messageId,List(JsonValidationError(List(error.path.missing),List()))))"
    }

    "default confirmation of delivery to true if not present in request but its overridden in config" in new Setup {
      val expectedStatus: Int                           = OK
      val messageCaptor: ArgumentCaptor[MessageRequest] = ArgumentCaptor.forClass(classOf[MessageRequest])
      when(outboundServiceMock.sendMessage(messageCaptor.capture())).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(message))

      status(result) shouldBe expectedStatus
      messageCaptor.getValue.confirmationOfDelivery shouldBe Some(true)
    }

    "confirmation of delivery field is true when true in the request" in new Setup {
      val expectedStatus: Int                           = OK
      val messageCaptor: ArgumentCaptor[MessageRequest] = ArgumentCaptor.forClass(classOf[MessageRequest])
      when(outboundServiceMock.sendMessage(messageCaptor.capture())).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(message + ("confirmationOfDelivery" -> JsBoolean(true))))

      status(result) shouldBe expectedStatus
      messageCaptor.getValue.confirmationOfDelivery shouldBe Some(true)
    }

    "confirmation of delivery field is false when false in the request" in new Setup {
      val expectedStatus: Int                           = OK
      val messageCaptor: ArgumentCaptor[MessageRequest] = ArgumentCaptor.forClass(classOf[MessageRequest])
      when(outboundServiceMock.sendMessage(messageCaptor.capture())).thenReturn(successful(outboundSoapMessage))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(message + ("confirmationOfDelivery" -> JsBoolean(false))))

      status(result) shouldBe expectedStatus
      messageCaptor.getValue.confirmationOfDelivery shouldBe Some(false)
    }

    "return bad request when there is a problem parsing the WSDL" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(failed(new WSDLException("the fault code", "the error")))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(message))

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "statusCode").as[Int] shouldBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] shouldBe "WSDLException: faultCode=the fault code: the error"
    }

    "return not found when the operation is missing in the WSDL definition" in new Setup {
      when(outboundServiceMock.sendMessage(*)).thenReturn(failed(new NotFoundException(s"Operation foobar not found")))

      val result: Future[Result] = underTest.message()(fakeRequest.withBody(message))

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "statusCode").as[Int] shouldBe NOT_FOUND
      (contentAsJson(result) \ "message").as[String] shouldBe "Operation foobar not found"
    }
  }
}
