/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformoutboundsoap.util

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.{MILLIS, MINUTES}
import java.util.UUID.randomUUID
import scala.concurrent.duration.Duration

import play.api.http.Status.OK

import uk.gov.hmrc.apiplatformoutboundsoap.models._

trait TestDataFactory {
  private val ccn2HttpStatus = OK
  lazy val now               = Instant.now
  val shortlyAfterNow        = now.plus(1, MINUTES)
  private val knownInstant   = Instant.parse("2023-08-10T10:11:12.123456Z")
  val knownInstantAfterMongo = knownInstant.truncatedTo(MILLIS)

  def globalId             = {
    randomUUID
  }
  val expectedInterval     = Duration("10s")
  val retryDuration = Duration("30s")
  val expectedRetryAt      = knownInstant.plus(java.time.Duration.ofMillis(expectedInterval.toMillis))
  val distantFutureRetryAt = now.plus(java.time.Duration.ofHours(1))
  val messageId            = "123"
  val notificationUrl      = "http://somenotification.url"
  val destinationUrl       = "http://example.com:1234/CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS"
  val privateHeaders       = Some(List(PrivateHeader(name = "name1", value = "value1"), PrivateHeader(name = "name2", value = "value2")))

  def expectedSoapEnvelope(extraHeaders: String = ""): String = {
    s"""<?xml version='1.0' encoding='utf-8'?>
       |<soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://www.w3.org/2005/08/addressing">
       |<soapenv:Header>
       |<wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS/IE4N03notifyERiskAnalysisHit</wsa:Action>
       |<ccnm:MessageHeader xmlns:ccnm="http://ccn2.ec.eu/CCN2.Service.Platform.Common.Schema">
       |<ccnm:Version>1.0</ccnm:Version>
       |<ccnm:SendingDateAndTime>2020-04-30T12:15:58.000Z</ccnm:SendingDateAndTime>
       |<ccnm:MessageType>CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS/IE4N03notifyERiskAnalysisHit</ccnm:MessageType>
       |<ccnm:RequestCoD>false</ccnm:RequestCoD>
       |</ccnm:MessageHeader>
       |$extraHeaders
       |</soapenv:Header>
       |<soapenv:Body>
       |<nsGHXkF:IE4N03notifyERiskAnalysisHitReqMsg xmlns:nsGHXkF="http://xmlns.ec.eu/BusinessActivityService/ICS/IRiskAnalysisOrchestrationBAS/V1">
       |<IE4N03 xmlns="urn:wco:datamodel:WCO:CIS:1"><riskAnalysis>example</riskAnalysis></IE4N03>
       |</nsGHXkF:IE4N03notifyERiskAnalysisHitReqMsg>
       |</soapenv:Body>
       |</soapenv:Envelope>""".stripMargin.replaceAll("\n", "")
  }

  val pendingOutboundSoapMessage = {
    PendingOutboundSoapMessage(globalId = globalId, messageId = messageId, soapMessage = "<xml><e>thing</e></xml>", destinationUrl = destinationUrl, createDateTime = now)
  }

  val failedOutboundSoapMessage = {
    FailedOutboundSoapMessage(globalId, messageId, expectedSoapEnvelope(), destinationUrl = destinationUrl, now, Some(ccn2HttpStatus), notificationUrl = Some(notificationUrl))
  }

  val sentOutboundSoapMessage = {
    SentOutboundSoapMessage(
      globalId,
      messageId,
      expectedSoapEnvelope(),
      destinationUrl = destinationUrl,
      now,
      Some(ccn2HttpStatus),
      sentDateTime = Some(shortlyAfterNow),
      notificationUrl = Some(notificationUrl)
    )
  }

  val sentOutboundSoapMessageWithAllFields = {
    SentOutboundSoapMessage(globalId, messageId, "envelope", "some url", now, Some(ccn2HttpStatus), None, None, None, Some(knownInstant), Some(List(PrivateHeader("test", "value"))))
  }

  def listRetryingOutboundSoapMessage(numberToReturn: Int): List[RetryingOutboundSoapMessage] = {
    (for {
      i <- 1 to numberToReturn
      v  = RetryingOutboundSoapMessage(
             randomUUID,
             messageId,
             expectedSoapEnvelope(),
             destinationUrl = destinationUrl,
             now,
             now.minus(i, ChronoUnit.HOURS),
             Some(ccn2HttpStatus),
             notificationUrl = Some(notificationUrl),
             privateHeaders = privateHeaders
           )
    } yield v).toList
  }

  val retryingOutboundSoapMessage = RetryingOutboundSoapMessage(
    globalId,
    messageId,
    expectedSoapEnvelope(),
    destinationUrl = destinationUrl,
    knownInstant,
    expectedRetryAt,
    Some(ccn2HttpStatus),
    notificationUrl = Some(notificationUrl),
    privateHeaders = privateHeaders
  )

  val retryingOutboundSoapMessageFutureRetryTime = RetryingOutboundSoapMessage(
    globalId,
    messageId,
    expectedSoapEnvelope(),
    destinationUrl = destinationUrl,
    knownInstant,
    distantFutureRetryAt,
    Some(ccn2HttpStatus),
    notificationUrl = Some(notificationUrl),
    privateHeaders = privateHeaders
  )

  val codSoapMessage = {
    CodSoapMessage(
      globalId,
      messageId,
      "<xml><e>thing</e></xml>",
      "http://destinat.ion",
      Instant.now,
      Some(ccn2HttpStatus),
      codMessage = Some("<xml>foobar</xml>")
    )
  }

  val coeSoapMessage = {
    CoeSoapMessage(
      globalId,
      messageId,
      "<xml><e>thing</e></xml>",
      "http://destinat.ion",
      Instant.now,
      Some(ccn2HttpStatus),
      coeMessage = Some("<xml>foobar</xml>")
    )
  }
}
