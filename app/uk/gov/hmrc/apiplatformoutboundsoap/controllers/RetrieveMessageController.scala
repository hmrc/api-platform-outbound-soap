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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.apiplatformoutboundsoap.{ErrorCode, JsErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RetrieveMessageController @Inject() (cc: ControllerComponents, messageRepository: OutboundMessageRepository)(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def message(id: String): Action[AnyContent] = Action.async {
    id.trim match {
      case "" => Future.successful(BadRequest(JsErrorResponse(ErrorCode.BAD_REQUEST, "id should not be empty")))
      case _  => messageRepository.findById(id).flatMap(maybeOutboundSoapMessage =>
          if (maybeOutboundSoapMessage.nonEmpty) {
            Future.successful(Ok(Json.toJson(maybeOutboundSoapMessage)))
          } else {
            Future.successful(NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, s"id [${id}] could not be found")))
          }
        ) recover {
          case e: Exception => InternalServerError(JsErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, "error querying database"))
        }
    }
  }
}
