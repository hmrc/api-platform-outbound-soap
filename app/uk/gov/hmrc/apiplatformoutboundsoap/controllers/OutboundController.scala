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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.{messageRequestFormatter, outboundMessageRequestFormatter, messageResponseFormatter}
import uk.gov.hmrc.apiplatformoutboundsoap.models.{MessageRequest, SoapMessageStatus, OutboundMessageRequest}
import uk.gov.hmrc.apiplatformoutboundsoap.services.OutboundService
import uk.gov.hmrc.apiplatformoutboundsoap.templates.xml.ie4n03Template
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import javax.wsdl.WSDLException
import scala.concurrent.ExecutionContext

@Singleton
class OutboundController @Inject()(cc: ControllerComponents,
                                   outboundConnector: OutboundConnector,
                                   outboundService: OutboundService)
                                  (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def sendMessage(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OutboundMessageRequest] { outboundMessageRequest =>
      val renderedMessage: String = ie4n03Template.render(outboundMessageRequest.message).body
      outboundConnector.postMessage(renderedMessage).map(new Status(_))
    }
  }

  def message(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[MessageRequest] { messageRequest =>
      outboundService.sendMessage(messageRequest)
        .map(outboundSoapMessage => Ok(Json.toJson(SoapMessageStatus(outboundSoapMessage.globalId, outboundSoapMessage.messageId, outboundSoapMessage.status))))
        .recover(recovery)
    }
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case e: WSDLException => BadRequest(e.getMessage)
    case e: NotFoundException => NotFound(e.message)
  }
}
