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
    when(mockAppConfig.ccn2Username).thenReturn(ccn2Username)
    when(mockAppConfig.ccn2Password).thenReturn(ccn2Password)
    when(mockAppConfig.cryptoKeystoreLocation).thenReturn("KeyStore.jks")
    when(mockAppConfig.keystoreAlias).thenReturn("Joe.Bloggs")
    when(mockAppConfig.keystorePassword).thenReturn("password")
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

  val expectedResultWithSignature: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
      |<soapenv:Header>
        |<wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" soapenv:mustUnderstand="true" soapenv:role="CCN2.Platform">
          |<ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="SIG-7f87542b-37d3-4dc5-849b-44df20c6a74e">
            |<ds:SignedInfo>
            |<ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
            |<ec:InclusiveNamespaces xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#" PrefixList="soapenv"/>
            |</ds:CanonicalizationMethod>
            |<ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
            |<ds:Reference URI="#id-608f2a88-3c9f-492f-8cde-b4f5ddcb485c">
            |<ds:Transforms>
            |<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            |</ds:Transforms>
            |<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
            |<ds:DigestValue>vdo/p3UAkm3/mqCawqnS7huTpVu51mNtiFuCgdxYJ6I=</ds:DigestValue>
            |</ds:Reference>
            |</ds:SignedInfo>
            |<ds:SignatureValue>YdN/PGpQxaeGp7iWVl8AGPK3PGomtYhRaNkz66pScqY4jmp224rwkzCwo5URuUuy0QUVUn12l5aH
            |5boiGYqdHKywVd7kfldluGtHXObJpj8L9aypdkPou1hvxR/IHvOPtEh+g6hRP8TLjMiC+naw5hVq
            |k4HNU34AviU3wJKcFrT2phTUDKJb8J2QE2vkTi/vtgSwTOSjmNdD8qsHj1jk+4gRqdEPb8gx4J5S
            |mN7ZqWzVgpsyPPjYYGJkVE4MbuQ6jWAm9ZeT2AUHfMvtw/4MIvCcaIRpW3/508+3MxwjPZm6D+wV
            |Hg5lZWK58+1DXbXOvxlNimrRZzj2V8fuiDqWGQ==</ds:SignatureValue>
            |<ds:KeyInfo Id="KI-f5dc16a2-704c-4361-bc69-a875ff294b6b">
              |<wsse:SecurityTokenReference wsu:Id="STR-f4491d8c-abba-45d6-8194-4d1f2ce6ed23" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                |<ds:X509Data>
                  |<ds:X509IssuerSerial>
                    |<ds:X509IssuerName>CN=Sam,OU=Coding,O=HMRC,L=Leeds,ST=Yorkshire,C=GB</ds:X509IssuerName>
                    |<ds:X509SerialNumber>787851343</ds:X509SerialNumber>
                  |</ds:X509IssuerSerial>
                |</ds:X509Data>
              |</wsse:SecurityTokenReference>
            |</ds:KeyInfo>
          |</ds:Signature>
        |</wsse:Security>
      |</soapenv:Header>
      |<soapenv:Body xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" wsu:Id="id-608f2a88-3c9f-492f-8cde-b4f5ddcb485c"/>
      |</soapenv:Envelope>
      |
      |""".stripMargin

  "addUsernameToken" should {
    "add the username token security header" in new Setup {
      val envelope: SOAPEnvelope = getSOAP12Factory.getDefaultEnvelope

      val result: String = underTest.addUsernameToken(envelope)

      getXmlDiff(result, expectedResult).build().hasDifferences shouldBe false
    }
  }

  "addSignature" should {
    "add a signature header to the SOAP envelope" in new Setup {
      val envelope: SOAPEnvelope = getSOAP12Factory.getDefaultEnvelope

      val result: String = underTest.addSignature(envelope)
      getXmlDiff(result, expectedResultWithSignature).build().hasDifferences shouldBe false
    }
  }

  private def getXmlDiff(actual: String, expected: String): DiffBuilder = {
    compare(expected)
      .withTest(actual)
      .withNodeMatcher(new DefaultNodeMatcher(byName))
      .withAttributeFilter(attribute => !Seq("wsu:Id", "Id", "URI").contains(attribute.getName))
      .withNodeFilter(node => !Seq("ds:DigestValue", "ds:SignatureValue").contains(node.getNodeName))
      .checkForIdentical
      .ignoreWhitespace
  }
}
