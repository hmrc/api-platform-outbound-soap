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
import uk.gov.hmrc.apiplatformoutboundsoap.services.InboundService
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import javax.inject.{Inject, Singleton}
import javax.wsdl.WSDLException
import scala.concurrent.ExecutionContext

@Singleton
class ConfirmationController @Inject()(cc: ControllerComponents,
                                       inboundService: InboundService)
                                      (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def message(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[String] { confirmationRequest =>
      inboundService.processConfirmation(confirmationRequest, request.headers.get("x-soap-action")) map {
        _ => ()
        Ok
      }
    }
  }
  //TODO: add recovery method
  //        .recover(recovery)


  private def recovery: PartialFunction[Throwable, Result] = {
    case e: WSDLException => BadRequest(Json.toJson(ErrorResponse(BAD_REQUEST, e.getMessage)))
    case e: NotFoundException => NotFound(Json.toJson(ErrorResponse(NOT_FOUND, e.message)))
  }
}
