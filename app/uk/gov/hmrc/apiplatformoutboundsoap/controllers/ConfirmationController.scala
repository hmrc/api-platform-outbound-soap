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
import uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders.ValidateConfirmationTypeAction
import uk.gov.hmrc.apiplatformoutboundsoap.models.DeliveryStatus
import uk.gov.hmrc.apiplatformoutboundsoap.models.common._
import uk.gov.hmrc.apiplatformoutboundsoap.services.ConfirmationService
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ConfirmationController @Inject()(cc: ControllerComponents,
                                       confirmationService: ConfirmationService,
                                      validateConfirmationTypeAction: ValidateConfirmationTypeAction)
                                      (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def message: Action[String] = (Action andThen validateConfirmationTypeAction).async(parse.text) { implicit request =>
    val confirmation = request.headers.get("x-soap-action").map(d => DeliveryStatus.withNameInsensitive(d))
    val result = confirmationService.processConfirmation(Some(request.body), confirmation.get)
    result map {
      case NoContentUpdateResult => NoContent
       case _ => NotFound
    }}

  /*  val deliveryStatus: Option[DeliveryStatus] = confirmation.map(
      c => DeliveryStatus.withNameInsensitive(c))
    confirmation.map(c => DeliveryStatus.withNameInsensitive(c)).map(ds =>
      confirmationService.processConfirmation(Some(request.body), ds)) map {
      case NoContentUpdateResult => NoContent
      case _ => NotFound
    }*/
  /* fromTry(Try({
     confirmation.map(c => DeliveryStatus.withNameInsensitive(c)).map(ds =>
       request.map(conf => confirmationService.processConfirmation(Some(request.body), ds)))
     Ok
   }).recover(recovery))*/

  /*def deleteByIntegrationId(integrationId: IntegrationId): Action[AnyContent] = Action.async { implicit request =>
    integrationService.deleteByIntegrationId(integrationId).map {
      case NoContentDeleteApiResult => NoContent
      case _ => NotFound
}
    }*/


  private def recovery: PartialFunction[Throwable, Result] = {
    case e: java.util.NoSuchElementException => BadRequest(e.getMessage)
    case e: NotFoundException => NotFound(Json.toJson(ErrorResponse(NOT_FOUND, e.message)))
  }
}