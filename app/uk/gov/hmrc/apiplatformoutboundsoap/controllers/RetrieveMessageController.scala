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

import play.api.Logging
import play.api.libs.json.{JsObject, Json, OFormat, OWrites}
import play.api.mvc._
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.models.JsonFormats._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.OutboundMessageRepository
import uk.gov.hmrc.apiplatformoutboundsoap.{ErrorCode, JsErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RetrieveMessageController @Inject()(cc: ControllerComponents,
                                          messageRepository: OutboundMessageRepository)
                                         (implicit ec: ExecutionContext)
  extends BackendController(cc) with Logging {

  def message(id: String): Action[AnyContent] = (Action).async {
    id.trim match {
      case "" => Future.successful(BadRequest(JsErrorResponse(ErrorCode.BAD_REQUEST, "id should not be empty")))
      case _ => messageRepository.findById(id).flatMap(maybeOutboundSoapMessage =>
        if (maybeOutboundSoapMessage.nonEmpty) {
          Future.successful(Ok(Json.toJson(maybeOutboundSoapMessage)))
        } else {
          Future.successful(NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, s"id [${id}] could not be found")))
        }
      )
    }
  }

  implicit val outboundSoapMessageWrites: OWrites[OutboundSoapMessage] = new OWrites[OutboundSoapMessage] {
    override def writes(soapMessage: OutboundSoapMessage): JsObject = soapMessage match {
      case r @ RetryingOutboundSoapMessage(_, _, _, _, _, _, _, _, _, _) =>
        retryingSoapMessageFormatter.writes(r) ++ Json.obj(
          "status" -> SendingStatus.RETRYING.entryName
        )
      case f @ FailedOutboundSoapMessage(_, _, _, _, _, _, _, _, _) =>
        failedSoapMessageFormatter.writes(f) ++ Json.obj(
          "status" -> SendingStatus.FAILED.entryName
        )
      case s @ SentOutboundSoapMessage(_, _, _, _, _, _, _, _, _) =>
        sentSoapMessageFormatter.writes(s) ++ Json.obj(
          "status" -> SendingStatus.SENT.entryName
        )
      case cod @ CodSoapMessage(_, _, _, _, _, _, _, _, _) =>
        codSoapMessageFormatter.writes(cod) ++ Json.obj(
          "status" -> DeliveryStatus.COD.entryName
        )
      case coe @ CoeSoapMessage(_, _, _, _, _, _, _, _, _) =>
        coeSoapMessageFormatter.writes(coe) ++ Json.obj(
          "status" -> DeliveryStatus.COE.entryName
        )
    }
  }
  implicit val outboundSoapMessageFormatter: OFormat[OutboundSoapMessage] = OFormat(Json.reads[OutboundSoapMessage], outboundSoapMessageWrites)
}
