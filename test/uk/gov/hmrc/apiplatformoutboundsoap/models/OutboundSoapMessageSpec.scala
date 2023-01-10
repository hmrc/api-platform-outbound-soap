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

package uk.gov.hmrc.apiplatformoutboundsoap.models

import java.time.Instant
import java.util.UUID

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OutboundSoapMessageSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  private val ccnHttpStatus: Int = 200
  private val now                = Instant.now
  val retryingMessage            = RetryingOutboundSoapMessage(UUID.randomUUID(), "11111", "some retrying message", "some destination url", now, now, ccnHttpStatus)
  val failedMessage              = FailedOutboundSoapMessage(UUID.randomUUID(), "22222", "failed message", "some destination url", now, ccnHttpStatus)
  val sentMessage                = SentOutboundSoapMessage(UUID.randomUUID(), "33333", "sent message", "some destination url", now, ccnHttpStatus)
  val coeMessage                 = CoeSoapMessage(UUID.randomUUID(), "44444", "coe message", "some destination url", now, ccnHttpStatus)
  val codMessage                 = CodSoapMessage(UUID.randomUUID(), "55555", "cod message", "some destination url", now, ccnHttpStatus)

  "typeNaming" should {
    "return correct type for RetryingOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(retryingMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.RETRYING
    }

    "return correct type for FailedOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(failedMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.FAILED
    }

    "return correct type for SentOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(sentMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.SENT
    }
    "return correct type for CoeSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(coeMessage.getClass.getCanonicalName)
      typeName shouldBe DeliveryStatus.COE
    }
    "return correct type for CodSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(codMessage.getClass.getCanonicalName)
      typeName shouldBe DeliveryStatus.COD
    }
    "throw exception for invalid type" in {
      val e: IllegalArgumentException = intercept[IllegalArgumentException] {
        OutboundSoapMessage.typeToStatus("a string".getClass.getCanonicalName)
      }
      e.getMessage should include("java.lang.String is not a valid class")
    }
  }
}
