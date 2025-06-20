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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatformoutboundsoap.util.TestDataFactory

class OutboundSoapMessageSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar with TestDataFactory {

  "typeNaming" should {
    "return correct type for RetryingOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(retryingOutboundSoapMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.RETRYING
    }

    "return correct type for PendingOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(pendingOutboundSoapMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.PENDING
    }

    "return correct type for FailedOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(failedOutboundSoapMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.FAILED
    }

    "return correct type for SentOutboundSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(sentOutboundSoapMessage.getClass.getCanonicalName)
      typeName shouldBe SendingStatus.SENT
    }
    "return correct type for CoeSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(coeSoapMessage.getClass.getCanonicalName)
      typeName shouldBe DeliveryStatus.COE
    }
    "return correct type for CodSoapMessage" in {
      val typeName = OutboundSoapMessage.typeToStatus(codSoapMessage.getClass.getCanonicalName)
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
