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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import akka.stream.Materializer
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.io.Source.fromInputStream

class OutboundControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ArgumentMatchersSugar {
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    val outboundConnectorMock: OutboundConnector = mock[OutboundConnector]

    val underTest = new OutboundController(Helpers.stubControllerComponents(), outboundConnectorMock)
  }

  "sendMessage" should {
    val fakeRequest = FakeRequest("POST", "/send-message")

    "return the status returned by the outbound connector" in new Setup {
      val expectedStatus: Int = OK
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(expectedStatus))

      val result: Future[Result] = underTest.sendMessage()(fakeRequest.withBody(Json.obj("message" -> "<IE4N03>example</IE4N03>")))

      status(result) shouldBe expectedStatus
    }

    "send the expected IE4N03 message within a SOAP envelope" in new Setup {
      val expectedIe4n03Message: String = fromInputStream(getClass.getResourceAsStream("/ie4n03.xml")).mkString
      val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(outboundConnectorMock.postMessage(messageCaptor.capture())).thenReturn(successful(OK))

      val result: Future[Result] = underTest.sendMessage()(fakeRequest.withBody(Json.obj("message" -> "<IE4N03>example</IE4N03>")))

      messageCaptor.getValue shouldBe expectedIe4n03Message
    }

    "return bad request when the request json body is invalid" in new Setup {
      when(outboundConnectorMock.postMessage(*)).thenReturn(successful(OK))

      val result: Future[Result] = underTest.sendMessage()(fakeRequest.withBody(Json.obj("invalid" -> "<IE4N03>example</IE4N03>")))

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid OutboundMessageRequest payload: List((/message,List(JsonValidationError(List(error.path.missing),WrappedArray()))))"
    }
  }
}
