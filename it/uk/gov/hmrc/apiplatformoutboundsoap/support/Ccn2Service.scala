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

package uk.gov.hmrc.apiplatformoutboundsoap.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait Ccn2Service {
  def primeCcn2Endpoint(body: String, status : Int): StubMapping = {
    stubFor(post(urlPathEqualTo("/"))
      .willReturn(
        aResponse()
        .withBody(body)
        .withStatus(status)
      )
    )
  }

  def stubCcn2Endpoint(body: String, fault: Fault): StubMapping = {
    stubFor(post(urlPathEqualTo("/"))
      .willReturn(
        aResponse()
        .withBody(body)
        .withFault(fault)
      )
    )
  }

  def verifyRequestBody(expectedRequestBody: String): Unit = {
    verify(postRequestedFor(urlPathEqualTo("/"))
      .withRequestBody(equalTo(expectedRequestBody)))
  }

  def verifyHeader(headerName: String, headerValue: String): Unit = {
    verify(postRequestedFor(urlPathEqualTo("/")).withHeader(headerName, equalTo(headerValue)))
  }
}
