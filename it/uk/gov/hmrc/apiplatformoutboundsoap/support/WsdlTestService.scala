package uk.gov.hmrc.apiplatformoutboundsoap.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.test.Helpers.OK

import scala.io.Source.fromInputStream

trait WsdlTestService {

  /*
    Tuples with resources in the format (URL -> file location).
    The paths are different to make sure the imports are being resolved through HTTP rather than using the filesystem.
   */
  private val resources: Seq[(String, String)] = Seq(
    "/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl" -> "/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl",
    "/imports/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_1.0.0.wsdl" -> "/definitions/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_1.0.0.wsdl",
    "/imports/CCN2.Service.Platform.SecurityPolicies.wsdl" -> "/definitions/CCN2.Service.Platform.SecurityPolicies.wsdl",
    "/imports/xsd/ICCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS.xsd" -> "/definitions/ICCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS.xsd",
    "/imports/xsd/Monitoring.xsd" -> "/definitions/Monitoring.xsd",
    "/imports/xsd/RiskAnalysisOrchestrationBASServiceType.xsd" -> "/definitions/RiskAnalysisOrchestrationBASServiceType.xsd",
    "/imports/xsd/IE4S01.xsd" -> "/definitions/IE4S01.xsd",
    "/imports/xsd/IE4N03.xsd" -> "/definitions/IE4N03.xsd",
    "/imports/xsd/IE4S02.xsd" -> "/definitions/IE4S02.xsd",
    "/imports/xsd/ctypes.xsd" -> "/definitions/ctypes.xsd",
    "/imports/xsd/htypes.xsd" -> "/definitions/htypes.xsd",
    "/imports/xsd/stypes.xsd" -> "/definitions/stypes.xsd"
  )

  def primeWsdlEndpoint(): Seq[StubMapping] = {
    resources.map(r => primeResource(r._1, fromInputStream(getClass.getResourceAsStream(r._2)).mkString))
  }

  private def primeResource(url: String, body: String): StubMapping = {
    stubFor(get(urlPathEqualTo(url))
      .willReturn(
        aResponse()
          .withBody(body)
          .withStatus(OK)
      )
    )
  }
}
