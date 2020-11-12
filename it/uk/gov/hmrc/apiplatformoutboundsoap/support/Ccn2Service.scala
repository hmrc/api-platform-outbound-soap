package uk.gov.hmrc.apiplatformoutboundsoap.support

import com.github.tomakehurst.wiremock.client.WireMock._
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

  def verifyRequestBody(expectedRequestBody: String): Unit = {
    verify(postRequestedFor(urlPathEqualTo("/"))
      .withRequestBody(equalTo(expectedRequestBody)))
  }

  def verifyHeader(headerName: String, headerValue: String): Unit = {
    verify(postRequestedFor(urlPathEqualTo("/")).withHeader(headerName, equalTo(headerValue)))
  }
}
