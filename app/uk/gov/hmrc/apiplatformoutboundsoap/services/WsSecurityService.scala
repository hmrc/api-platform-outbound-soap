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

import java.util.Properties
import javax.inject.{Inject, Singleton}

import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.util.XMLUtils.toDOM
import org.apache.wss4j.common.WSS4JConstants
import org.apache.wss4j.common.WSS4JConstants.PASSWORD_TEXT
import org.apache.wss4j.common.crypto.{Crypto, CryptoFactory}
import org.apache.wss4j.common.util.XMLUtils.prettyDocumentToString
import org.apache.wss4j.dom.WSConstants
import org.apache.wss4j.dom.message.{WSSecHeader, WSSecSignature, WSSecUsernameToken}

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig

@Singleton
class WsSecurityService @Inject() (appConfig: AppConfig) {
  val ROLE = "CCN2.Platform"

  val crytoProperties: Properties = new Properties()
  crytoProperties.setProperty("org.apache.wss4j.crypto.merlin.keystore.file", appConfig.cryptoKeystoreLocation)
  crytoProperties.setProperty("org.apache.wss4j.crypto.merlin.keystore.password", appConfig.keystorePassword)
  lazy val crypto: Crypto         = CryptoFactory.getInstance(crytoProperties)

  def addUsernameToken(soapEnvelope: SOAPEnvelope): String = {
    val secHeader: WSSecHeader = createSecurityHeader(soapEnvelope)
    val builder                = new WSSecUsernameToken(secHeader)
    builder.setPasswordType(PASSWORD_TEXT)
    builder.setUserInfo(appConfig.ccn2Username, appConfig.ccn2Password)
    prettyDocumentToString(builder.build())
  }

  def addSignature(soapEnvelope: SOAPEnvelope): String = {
    val secHeader: WSSecHeader = createSecurityHeader(soapEnvelope)
    val builder                = new WSSecSignature(secHeader)
    builder.setKeyIdentifierType(WSConstants.ISSUER_SERIAL)
    builder.setUserInfo(appConfig.keystoreAlias, appConfig.keystorePassword)
    builder.setDigestAlgo(WSS4JConstants.SHA256)
    builder.setSignatureAlgorithm(WSS4JConstants.RSA_SHA256)
    prettyDocumentToString(builder.build(crypto))
  }

  private def createSecurityHeader(soapEnvelope: SOAPEnvelope) = {
    val envelopeDocument       = toDOM(soapEnvelope).getOwnerDocument
    val secHeader: WSSecHeader = new WSSecHeader(ROLE, envelopeDocument)
    secHeader.insertSecurityHeader()
    secHeader
  }
}
