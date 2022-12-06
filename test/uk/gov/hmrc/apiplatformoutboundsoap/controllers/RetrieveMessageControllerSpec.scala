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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository

import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class RetrieveMessageControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val repositoryMock: OutboundMessageRepository = mock[OutboundMessageRepository]
    val underTest = new RetrieveMessageController(Helpers.stubControllerComponents(), repositoryMock)
  }

  "message" should {
    import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.instantFormat
    val fakeRequest = FakeRequest("GET", "/retrieve")
    val ccn2HttpStatus = 200
    val failedOutboundSoapMessage = FailedOutboundSoapMessage(UUID.randomUUID(), "some messageId", "<xml><e>thing</e></xml>",
      "http://destinat.ion", Instant.now, ccn2HttpStatus)
    val sentOutboundSoapMessage = SentOutboundSoapMessage(UUID.randomUUID(), "some messageId", "<xml><e>thing</e></xml>",
      "http://destinat.ion", Instant.now, ccn2HttpStatus)
    val retryingOutboundSoapMessage = RetryingOutboundSoapMessage(UUID.randomUUID(), "some messageId", "<xml><e>thing</e></xml>",
      "http://destinat.ion", Instant.now, Instant.now, ccn2HttpStatus)
    val codSoapMessage = CodSoapMessage(UUID.randomUUID(), "some messageId", "<xml><e>thing</e></xml>", "http://destinat.ion",
      Instant.now, ccn2HttpStatus, codMessage = Some("<soap:Body><ccn2:CoD><ccn2:EventTimestamp>2021-03-10T09:30:10Z</ccn2:EventTimestamp></ccn2:CoD></soap:Body>"))
    val coeSoapMessage = CoeSoapMessage(UUID.randomUUID(), "some messageId", "<xml><e>thing</e></xml>", "http://destinat.ion",
      Instant.now, ccn2HttpStatus, coeMessage = Some("<coe><error>failed</error></coe>"))

    "return a failed message when supplied with an ID that exists" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.successful(Some(failedOutboundSoapMessage)))

      val result: Future[Result] = underTest.message("1234")(fakeRequest)

      status(result) shouldBe OK
      (contentAsJson(result) \ "globalId").as[UUID] shouldBe failedOutboundSoapMessage.globalId
      (contentAsJson(result) \ "messageId").as[String] shouldBe failedOutboundSoapMessage.messageId
      (contentAsJson(result) \ "soapMessage").as[String] shouldBe failedOutboundSoapMessage.soapMessage
      (contentAsJson(result) \ "destinationUrl").as[String] shouldBe failedOutboundSoapMessage.destinationUrl
      (contentAsJson(result) \ "createDateTime").as[Instant] shouldBe failedOutboundSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      (contentAsJson(result) \ "ccnHttpStatus").as[Int] shouldBe failedOutboundSoapMessage.ccnHttpStatus
      (contentAsJson(result) \ "status").as[SendingStatus] shouldBe SendingStatus.FAILED
    }

    "return a retrying message when supplied with an ID that exists" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.successful(Some(retryingOutboundSoapMessage)))

      val result: Future[Result] = underTest.message("1234")(fakeRequest)

      status(result) shouldBe OK
      (contentAsJson(result) \ "globalId").as[UUID] shouldBe retryingOutboundSoapMessage.globalId
      (contentAsJson(result) \ "messageId").as[String] shouldBe retryingOutboundSoapMessage.messageId
      (contentAsJson(result) \ "soapMessage").as[String] shouldBe retryingOutboundSoapMessage.soapMessage
      (contentAsJson(result) \ "destinationUrl").as[String] shouldBe retryingOutboundSoapMessage.destinationUrl
      (contentAsJson(result) \ "createDateTime").as[Instant] shouldBe retryingOutboundSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      (contentAsJson(result) \ "ccnHttpStatus").as[Int] shouldBe retryingOutboundSoapMessage.ccnHttpStatus
      (contentAsJson(result) \ "status").as[SendingStatus] shouldBe SendingStatus.RETRYING
    }

    "return a sent message when supplied with an ID that exists" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.successful(Some(sentOutboundSoapMessage)))

      val result: Future[Result] = underTest.message("1234")(fakeRequest)

      status(result) shouldBe OK
      (contentAsJson(result) \ "globalId").as[UUID] shouldBe sentOutboundSoapMessage.globalId
      (contentAsJson(result) \ "messageId").as[String] shouldBe sentOutboundSoapMessage.messageId
      (contentAsJson(result) \ "soapMessage").as[String] shouldBe sentOutboundSoapMessage.soapMessage
      (contentAsJson(result) \ "destinationUrl").as[String] shouldBe sentOutboundSoapMessage.destinationUrl
      (contentAsJson(result) \ "createDateTime").as[Instant] shouldBe sentOutboundSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      (contentAsJson(result) \ "ccnHttpStatus").as[Int] shouldBe sentOutboundSoapMessage.ccnHttpStatus
      (contentAsJson(result) \ "status").as[SendingStatus] shouldBe SendingStatus.SENT
    }

    "return a CoE message when supplied with an ID that exists" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.successful(Some(coeSoapMessage)))

      val result: Future[Result] = underTest.message("1234")(fakeRequest)

      status(result) shouldBe OK
      (contentAsJson(result) \ "globalId").as[UUID] shouldBe coeSoapMessage.globalId
      (contentAsJson(result) \ "messageId").as[String] shouldBe coeSoapMessage.messageId
      (contentAsJson(result) \ "soapMessage").as[String] shouldBe coeSoapMessage.soapMessage
      (contentAsJson(result) \ "destinationUrl").as[String] shouldBe coeSoapMessage.destinationUrl
      (contentAsJson(result) \ "createDateTime").as[Instant] shouldBe coeSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      (contentAsJson(result) \ "ccnHttpStatus").as[Int] shouldBe coeSoapMessage.ccnHttpStatus
      Some((contentAsJson(result) \ "coeMessage").as[String]) shouldBe coeSoapMessage.coeMessage
      (contentAsJson(result) \ "status").as[DeliveryStatus] shouldBe DeliveryStatus.COE
    }
    "return a CoD message when supplied with an ID that exists" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.successful(Some(codSoapMessage)))

      val result: Future[Result] = underTest.message("1234")(fakeRequest)

      status(result) shouldBe OK
      (contentAsJson(result) \ "globalId").as[UUID] shouldBe codSoapMessage.globalId
      (contentAsJson(result) \ "messageId").as[String] shouldBe codSoapMessage.messageId
      (contentAsJson(result) \ "soapMessage").as[String] shouldBe codSoapMessage.soapMessage
      (contentAsJson(result) \ "destinationUrl").as[String] shouldBe codSoapMessage.destinationUrl
      (contentAsJson(result) \ "createDateTime").as[Instant] shouldBe codSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      (contentAsJson(result) \ "ccnHttpStatus").as[Int] shouldBe codSoapMessage.ccnHttpStatus
      Some((contentAsJson(result) \ "codMessage").as[String]) shouldBe codSoapMessage.codMessage
      (contentAsJson(result) \ "status").as[DeliveryStatus] shouldBe DeliveryStatus.COD
    }

    "return a 404 response when supplied with an ID that does not exist" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.successful(None))

      val result: Future[Result] = underTest.message("1234")(fakeRequest)
      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "code").as[String] shouldBe "NOT_FOUND"
      (contentAsJson(result) \ "message").as[String] shouldBe "id [1234] could not be found"
    }

    "return a 500 response when exception in find" in new Setup {
      when(repositoryMock.findById(*)).thenReturn(Future.failed(new IOException("expected")))

      val result = underTest.message("1234")(fakeRequest)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe "error querying database"
    }

    "return a 400 response when supplied with an ID that is whitespace" in new Setup {
      for (elem <- Seq(" ", "\t", "", "\r")) {
        when(repositoryMock.findById(*)).thenReturn(Future.successful(None))

        val result: Future[Result] = underTest.message(elem)(fakeRequest)

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "code").as[String] shouldBe "BAD_REQUEST"
        (contentAsJson(result) \ "message").as[String] shouldBe "id should not be empty"
      }
    }
  }
}
