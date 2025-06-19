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
import java.time.temporal.ChronoUnit._

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.libs.json.Json

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats._
import uk.gov.hmrc.apiplatformoutboundsoap.util.TestDataFactory

class SoapMessageStatusSpec extends AnyWordSpec with Matchers with MockitoSugar with GuiceOneAppPerSuite with ArgumentMatchersSugar with TestDataFactory {
  val knownInstant  = Instant.parse("2023-08-10T10:11:12.123456Z")
  val truncatedNow  = now.truncatedTo(MILLIS)
  val mockAppConfig = mock[AppConfig]

  "from outboundsoapmessage" should {

    "not include sentDateTime if None" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(sentOutboundSoapMessage.copy(sentDateTime = Option.empty)))
      (json \ "sentDateTime").asOpt[Instant] shouldBe None
    }

    "not include privateHeaders if None" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(sentOutboundSoapMessage))
      (json \ "privateHeaders").asOpt[List[PrivateHeader]] shouldBe None
    }

    "include sentDateTime if present" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(sentOutboundSoapMessageWithAllFields))
      (json \ "sentDateTime").asOpt[String] shouldBe Some("2023-08-10T10:11:12.123Z")
    }

    "include sentDateTime if present and truncate fractions of a second to millis" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(
        sentOutboundSoapMessageWithAllFields
      ))
      (json \ "sentDateTime").asOpt[String] shouldBe Some("2023-08-10T10:11:12.123Z")
    }

    "include sentDateTime if present and always include milliseconds" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(
        sentOutboundSoapMessageWithAllFields.copy(sentDateTime = Some(knownInstant.truncatedTo(SECONDS)))
      ))
      (json \ "sentDateTime").asOpt[String] shouldBe Some("2023-08-10T10:11:12.000Z")
    }

    "include privateHeaders if present" in {
      val json = Json.toJson(SoapMessageStatus.fromOutboundSoapMessage(sentOutboundSoapMessageWithAllFields))
      (json \ "privateHeaders").asOpt[List[PrivateHeader]] shouldBe Some(List(PrivateHeader("test", "value")))
    }
  }
}
