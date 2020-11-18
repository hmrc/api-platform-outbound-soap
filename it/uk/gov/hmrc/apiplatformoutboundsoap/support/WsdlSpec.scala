/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.wsdl.extensions.soap12.SOAP12Operation
import javax.wsdl.{Binding, BindingOperation, Definition}
import org.apache.cxf.tools.wsdlto.core.WSDLDefinitionBuilder
import org.apache.cxf.{Bus, BusFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.collection.JavaConverters._

class WsdlSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with WireMockSupport with WsdlTestService {
  override implicit lazy val app: Application = appBuilder.build()

  protected  def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                 -> false,
        "auditing.enabled"                -> false
      )

  trait Setup {
    val bus: Bus = BusFactory.getDefaultBus
    val builder = new WSDLDefinitionBuilder(bus)
    val wsdlUrl = s"$wireMockBaseUrlAsString/CCN2.Service.Customs.Default.ICS.RiskAnalysisOrchestrationBAS_1.0.0_CCN2_1.0.0.wsdl"
    primeWsdlEndpoint()
  }

  "WSDL parsing test" should {
    "parse WSDL with imports" in new Setup {
      val wsdlDefinition: Definition = builder.build(wsdlUrl)

      wsdlDefinition.getImports() should have size 1
    }

    "contain the SOAP action in the binding operation" in new Setup {
      val wsdlDefinition: Definition = builder.build(wsdlUrl)

      val binding: Binding = wsdlDefinition.getAllBindings.asScala.values.head.asInstanceOf[Binding]
      val bindingOperation: BindingOperation = binding.getBindingOperations.asScala.head.asInstanceOf[BindingOperation]
      val soapOperation: SOAP12Operation = bindingOperation.getExtensibilityElements.asScala.head.asInstanceOf[SOAP12Operation]
      soapOperation.getSoapActionURI shouldBe "CCN2.Service.Customs.EU.ICS.RiskAnalysisOrchestrationBAS/IE4N03notifyERiskAnalysisHit"
    }
  }
}
