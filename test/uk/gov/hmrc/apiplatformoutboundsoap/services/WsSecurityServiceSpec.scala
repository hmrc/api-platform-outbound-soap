/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformoutboundsoap.services

import org.apache.axiom.om.OMAbstractFactory.getSOAP12Factory
import org.apache.axiom.soap.SOAPEnvelope
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.DiffBuilder.compare
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig

import java.util.UUID.randomUUID

class WsSecurityServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val ccn2Username = "Joe Bloggs"
    val ccn2Password: String = randomUUID.toString
    val mockAppConfig: AppConfig = mock[AppConfig]
    val underTest = new WsSecurityService(mockAppConfig)

    val expectedResult: String =
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
             |<soapenv:Header>
                 |<wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                     | xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" soapenv:mustUnderstand="true" soapenv:role="CCN2.Platform">
                     |<wsse:UsernameToken wsu:Id="UsernameToken-acda9a63-7436-4af2-a0d2-cf96af942021">
                         |<wsse:Username>$ccn2Username</wsse:Username>
                         |<wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">$ccn2Password</wsse:Password>
                     |</wsse:UsernameToken>
                 |</wsse:Security>
             |</soapenv:Header>
             |<soapenv:Body/>
         |</soapenv:Envelope>""".stripMargin.replaceAll("\n", "")
  }

  "addUsernameToken" should {
    "add the username token security header" in new Setup {
      when(mockAppConfig.ccn2Username).thenReturn(ccn2Username)
      when(mockAppConfig.ccn2Password).thenReturn(ccn2Password)
      val envelope: SOAPEnvelope = getSOAP12Factory.getDefaultEnvelope

      val result: String = underTest.addUsernameToken(envelope)

      getXmlDiff(result, expectedResult).build().hasDifferences shouldBe false
    }
  }

  private def getXmlDiff(actual: String, expected: String): DiffBuilder = {
    compare(expected)
      .withTest(actual)
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .withAttributeFilter(attribute => !"wsu:Id".equalsIgnoreCase(attribute.getName))
      .checkForIdentical
      .ignoreWhitespace
  }
}
