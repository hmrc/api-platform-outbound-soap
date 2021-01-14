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

import org.apache.axiom.soap.SOAPEnvelope
import org.apache.axis2.util.XMLUtils.toDOM
import org.apache.wss4j.common.WSS4JConstants.PASSWORD_TEXT
import org.apache.wss4j.common.util.XMLUtils.prettyDocumentToString
import org.apache.wss4j.dom.message.{WSSecHeader, WSSecUsernameToken}
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig

import javax.inject.{Inject, Singleton}

@Singleton
class WsSecurityService @Inject()(appConfig: AppConfig) {
  val ROLE = "CCN2.Platform"

  def addUsernameToken(soapEnvelope: SOAPEnvelope): String = {
    val envelopeDocument = toDOM(soapEnvelope).getOwnerDocument

    val secHeader: WSSecHeader = new WSSecHeader(ROLE, envelopeDocument)
    secHeader.insertSecurityHeader()
    val builder = new WSSecUsernameToken(secHeader)
    builder.setPasswordType(PASSWORD_TEXT)
    builder.setUserInfo(appConfig.ccn2Username, appConfig.ccn2Password)
    prettyDocumentToString(builder.build())
  }
}
