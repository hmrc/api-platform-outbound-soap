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

package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import java.util.UUID

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.util.TestDataFactory

class MongoFormatterSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar with TestDataFactory {

  "format writes" should {
    val formatter = MongoFormatter.outboundSoapMessageWrites

    "correctly write a PENDING message" in {
      val msgJson: JsObject = formatter.writes(pendingOutboundSoapMessage)
      msgJson.values.size shouldBe 6
      msgJson.value.get("status") shouldBe Some(JsString("PENDING"))
    }

    "correctly write a COD message" in {
      val msgJson: JsObject =
        formatter.writes(CodSoapMessage(UUID.randomUUID(), "12334", "some cod message", "some destination url", Instant.now, Some(200), Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("COD"))
    }

    "correctly write a COE message" in {
      val msgJson: JsObject =
        formatter.writes(CoeSoapMessage(UUID.randomUUID(), "12334", "some coe message", "some destination url", Instant.now, Some(200), Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("COE"))
    }

    "correctly write a SENT message" in {
      val now               = Instant.now
      val msgJson: JsObject = formatter.writes(SentOutboundSoapMessage(
        UUID.randomUUID(),
        "12334",
        "sent message",
        "some destination url",
        Instant.now,
        Some(200),
        Some("notify url"),
        Some("msg"),
        None,
        Some(now)
      ))
      msgJson.values.size shouldBe 10
      msgJson.value.get("status") shouldBe Some(JsString("SENT"))
      msgJson.value.get("sentDateTime") shouldBe Some(MongoJavatimeFormats.instantFormat.writes(now))
    }

    "correctly write a FAILED message" in {
      val msgJson: JsObject =
        formatter.writes(FailedOutboundSoapMessage(UUID.randomUUID(), "12334", "failed message", "some destination url", Instant.now, Some(200), Some("notify url"), Some("msg")))
      msgJson.values.size shouldBe 9
      msgJson.value.get("status") shouldBe Some(JsString("FAILED"))
    }
    "correctly write a RETRYING message" in {
      val msgJson: JsObject = formatter.writes(RetryingOutboundSoapMessage(
        UUID.randomUUID(),
        "12334",
        "retrying message",
        "some destination url",
        Instant.now,
        Instant.now,
        Some(200),
        Some("notify url"),
        Some("msg")
      ))
      msgJson.values.size shouldBe 10
      msgJson.value.get("status") shouldBe Some(JsString("RETRYING"))
    }
  }

  "format reads" should {
    val formatter = MongoFormatter.outboundSoapMessageReads

    "correctly read a COD message" in {

      val messageId             = "ISALIVE-1703124554-V1"
      val createDateTimeInstant = Instant.now.truncatedTo(MILLIS)
      val createDateTimeJsValue = MongoJavatimeFormats.instantWrites.writes(createDateTimeInstant)

      val msgJson = Json.obj(
        "globalId"       -> "6fa2d156-5b35-4c0b-8f31-e3a7d4fe278a",
        "messageId"      -> messageId,
        "soapMessage"    -> "soap message",
        "destinationUrl" -> "https://ccn.conf.hmrc.gov.uk:443/CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBASV2",
        "createDateTime" -> createDateTimeJsValue,
        "ccnHttpStatus"  -> 202,
        "status"         -> "COD",
        "codMessage"     -> "cod message"
      )

      val msg = formatter.reads(msgJson)

      msg.isSuccess shouldBe true
      msg.get.isInstanceOf[CodSoapMessage] shouldBe true
      msg.get.messageId shouldBe messageId
      msg.get.createDateTime shouldBe createDateTimeInstant
      msg.get.codMessage shouldBe Some("cod message")
    }

    "correctly read a SENT message" in {

      val messageId             = "ISALIVE-1703124554-V1"
      val createDateTimeInstant = Instant.now.truncatedTo(MILLIS)
      val createDateTimeJsValue = MongoJavatimeFormats.instantWrites.writes(createDateTimeInstant)

      val msgJson = Json.obj(
        "globalId"       -> "6fa2d156-5b35-4c0b-8f31-e3a7d4fe278a",
        "messageId"      -> messageId,
        "soapMessage"    -> "soap message",
        "destinationUrl" -> "https://ccn.conf.hmrc.gov.uk:443/CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBASV2",
        "createDateTime" -> createDateTimeJsValue,
        "ccnHttpStatus"  -> 202,
        "status"         -> "SENT",
        "codMessage"     -> "cod message"
      )

      val msg = formatter.reads(msgJson)

      msg.isSuccess shouldBe true
      msg.get.isInstanceOf[SentOutboundSoapMessage] shouldBe true
      msg.get.messageId shouldBe messageId
      msg.get.createDateTime shouldBe createDateTimeInstant
    }

    "correctly read a PENDING message" in {

      val messageId             = "ISALIVE-1703124554-V1"
      val createDateTimeInstant = Instant.now.truncatedTo(MILLIS)
      val createDateTimeJsValue = MongoJavatimeFormats.instantWrites.writes(createDateTimeInstant)

      val msgJson = Json.obj(
        "globalId"       -> "6fa2d156-5b35-4c0b-8f31-e3a7d4fe278a",
        "messageId"      -> messageId,
        "soapMessage"    -> "soap message",
        "destinationUrl" -> "https://ccn.conf.hmrc.gov.uk:443/CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBASV2",
        "createDateTime" -> createDateTimeJsValue,
        "status"         -> "PENDING",
        "codMessage"     -> "cod message"
      )

      val msg = formatter.reads(msgJson)

      msg.isSuccess shouldBe true
      msg.get.isInstanceOf[PendingOutboundSoapMessage] shouldBe true
      msg.get.ccnHttpStatus shouldBe Option.empty
      msg.get.messageId shouldBe messageId
      msg.get.createDateTime shouldBe createDateTimeInstant
    }

    "correctly read a FAILED message" in {

      val messageId             = "ISALIVE-1703124554-V1"
      val createDateTimeInstant = Instant.now.truncatedTo(MILLIS)
      val createDateTimeJsValue = MongoJavatimeFormats.instantWrites.writes(createDateTimeInstant)

      val msgJson = Json.obj(
        "globalId"       -> "6fa2d156-5b35-4c0b-8f31-e3a7d4fe278a",
        "messageId"      -> messageId,
        "soapMessage"    -> "soap message",
        "destinationUrl" -> "https://ccn.conf.hmrc.gov.uk:443/CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBASV2",
        "createDateTime" -> createDateTimeJsValue,
        "ccnHttpStatus"  -> 202,
        "status"         -> "FAILED",
        "codMessage"     -> "cod message"
      )

      val msg = formatter.reads(msgJson)

      msg.isSuccess shouldBe true
      msg.get.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
      msg.get.messageId shouldBe messageId
      msg.get.createDateTime shouldBe createDateTimeInstant
    }

    "correctly read a COE message" in {

      val messageId             = "ISALIVE-1703124554-V1"
      val createDateTimeInstant = Instant.now.truncatedTo(MILLIS)
      val createDateTimeJsValue = MongoJavatimeFormats.instantWrites.writes(createDateTimeInstant)

      val msgJson = Json.obj(
        "globalId"       -> "6fa2d156-5b35-4c0b-8f31-e3a7d4fe278a",
        "messageId"      -> messageId,
        "soapMessage"    -> "soap message",
        "destinationUrl" -> "https://ccn.conf.hmrc.gov.uk:443/CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBASV2",
        "createDateTime" -> createDateTimeJsValue,
        "ccnHttpStatus"  -> 202,
        "status"         -> "COE",
        "codMessage"     -> "cod message",
        "coeMessage"     -> "coe message"
      )

      val msg = formatter.reads(msgJson)

      msg.isSuccess shouldBe true
      msg.get.isInstanceOf[CoeSoapMessage] shouldBe true
      msg.get.messageId shouldBe messageId
      msg.get.createDateTime shouldBe createDateTimeInstant
      msg.get.coeMessage shouldBe Some("coe message")
    }

    "correctly read a RETRYING message" in {

      val messageId             = "ISALIVE-1703124554-V1"
      val createDateTimeInstant = Instant.now.truncatedTo(MILLIS)
      val createDateTimeJsValue = MongoJavatimeFormats.instantWrites.writes(createDateTimeInstant)
      val retryDateTimeInstant  = Instant.now.truncatedTo(MILLIS)
      val retryDateTimeJsValue  = MongoJavatimeFormats.instantWrites.writes(retryDateTimeInstant)

      val msgJson = Json.obj(
        "globalId"       -> "6fa2d156-5b35-4c0b-8f31-e3a7d4fe278a",
        "messageId"      -> messageId,
        "soapMessage"    -> "soap message",
        "destinationUrl" -> "https://ccn.conf.hmrc.gov.uk:443/CCN2.Service.Customs.EU.ICS.ENSLifecycleManagementBASV2",
        "createDateTime" -> createDateTimeJsValue,
        "retryDateTime"  -> retryDateTimeJsValue,
        "ccnHttpStatus"  -> 202,
        "status"         -> "RETRYING",
        "codMessage"     -> "cod message",
        "coeMessage"     -> "coe message"
      )

      val msg = formatter.reads(msgJson)

      msg.isSuccess shouldBe true
      msg.get.isInstanceOf[RetryingOutboundSoapMessage] shouldBe true
      msg.get.messageId shouldBe messageId
      msg.get.createDateTime shouldBe createDateTimeInstant
      msg.get.coeMessage shouldBe Some("coe message")
    }
  }
}
