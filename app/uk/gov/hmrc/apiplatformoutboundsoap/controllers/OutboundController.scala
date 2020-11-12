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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apiplatformoutboundsoap.connectors.OutboundConnector
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats.outboundMessageRequestFormatter
import uk.gov.hmrc.apiplatformoutboundsoap.models.{ErrorCode, ErrorResponse, OutboundMessageRequest}
import uk.gov.hmrc.apiplatformoutboundsoap.templates.xml.ie4n03Template
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class OutboundController @Inject()(cc: ControllerComponents,
                                   outboundConnector: OutboundConnector)
                                  (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def sendMessage(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OutboundMessageRequest] { outboundMessageRequest =>
      val renderedMessage: String = ie4n03Template.render(outboundMessageRequest.message).body
      outboundConnector.postMessage(renderedMessage).map(new Status(_))
    }
  }

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {
    val invalidResult = Future.successful(BadRequest(ErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "JSON body is invalid against expected format")))
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(_)) => invalidResult
      case Failure(_) => invalidResult
    }
  }
}
