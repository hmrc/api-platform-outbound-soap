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

package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import org.joda.time.DateTime
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.apiplatformoutboundsoap.models.{CodSoapMessage, CoeSoapMessage, FailedOutboundSoapMessage, RetryingOutboundSoapMessage, SentOutboundSoapMessage}

import java.util.UUID

class MongoFormatterSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  "format" should {
    val formatter = MongoFormatter.outboundSoapMessageWrites
    "correctly write a COD message" in {
      val msgJson: JsObject = formatter.writes(CodSoapMessage(UUID.randomUUID(), "12334", "some cod message", "some destination url", DateTime.now, 200, Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("COD"))
    }
    "correctly write a COE message" in {
      val msgJson: JsObject = formatter.writes(CoeSoapMessage(UUID.randomUUID(), "12334", "some coe message", "some destination url", DateTime.now, 200, Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("COE"))
    }
    "correctly write a SENT message" in {
      val msgJson: JsObject = formatter.writes(SentOutboundSoapMessage(UUID.randomUUID(), "12334", "sent message", "some destination url", DateTime.now, 200, Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("SENT"))
    }
    "correctly write a FAILED message" in {
      val msgJson: JsObject = formatter.writes(FailedOutboundSoapMessage(UUID.randomUUID(), "12334", "failed message", "some destination url", DateTime.now, 200, Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("FAILED"))
    }
    "correctly write a RETRYING message" in {
      val msgJson: JsObject = formatter.writes(RetryingOutboundSoapMessage(UUID.randomUUID(), "12334", "retrying message", "some destination url", DateTime.now,DateTime.now, 200, Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 10
      msgJson.value.get("status") shouldBe Some(JsString("RETRYING"))
    }
  }
}
