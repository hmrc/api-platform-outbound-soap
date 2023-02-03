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
import java.time.temporal.ChronoUnit
import java.util.UUID

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status.OK
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats._

class SoapMessageStatusSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with ArgumentMatchersSugar {
  val now           = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  val mockAppConfig = mock[AppConfig]

  "from outboundsoapmessage" should {

    val noOptionalsOutboundSoapMessage: OutboundSoapMessage = SentOutboundSoapMessage(UUID.randomUUID, "123", "envelope", "some url", now, OK)

    val optionalsOutboundSoapMessage: OutboundSoapMessage =
      SentOutboundSoapMessage(UUID.randomUUID, "123", "envelope", "some url", now, OK, None, None, None, Some(now), Some(List(PrivateHeader("test", "value"))))

    "not include sentDateTime if None" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(noOptionalsOutboundSoapMessage))
      (json \ "sentDateTime").asOpt[Instant] shouldBe None
    }

    "not include privateHeaders if None" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(noOptionalsOutboundSoapMessage))
      (json \ "privateHeaders").asOpt[List[PrivateHeader]] shouldBe None
    }
    "include sentDateTime if present" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(optionalsOutboundSoapMessage))
      (json \ "sentDateTime").asOpt[Instant] shouldBe Some(now)
    }

    "include privateHeaders if present" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(optionalsOutboundSoapMessage))
      (json \ "privateHeaders").asOpt[List[PrivateHeader]] shouldBe Some(List(PrivateHeader("test", "value")))
    }
  }
}
