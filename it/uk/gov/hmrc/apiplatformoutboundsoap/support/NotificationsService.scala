package uk.gov.hmrc.apiplatformoutboundsoap.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait NotificationsService {
  def primeNotificationsEndpoint(status : Int): StubMapping = {
    stubFor(post(urlPathEqualTo("/"))
      .willReturn(
        aResponse()
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
