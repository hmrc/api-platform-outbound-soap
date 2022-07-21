/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders.ValidateConfirmationTypeAction
import uk.gov.hmrc.apiplatformoutboundsoap.models.DeliveryStatus
import uk.gov.hmrc.apiplatformoutboundsoap.models.common._
import uk.gov.hmrc.apiplatformoutboundsoap.services.ConfirmationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Node, NodeSeq}
import scala.language.postfixOps

@Singleton
class ConfirmationController @Inject()(cc: ControllerComponents,
                                       confirmationService: ConfirmationService,
                                      validateConfirmationTypeAction: ValidateConfirmationTypeAction)
                                      (implicit ec: ExecutionContext)
  extends BackendController(cc) with Logging{

  def message: Action[NodeSeq] = (Action andThen validateConfirmationTypeAction).async(parse.xml) { implicit request =>
    val xml: NodeSeq = request.body

    def callService(deliveryStatus: DeliveryStatus, id: String): Future[Result] = {
      if(id.trim.nonEmpty) {
        confirmationService.processConfirmation(xml, id.trim, deliveryStatus) map {
          case UpdateSuccessResult => Accepted
          case _ =>
            logger.warn(s"No message found with global ID [$id]. Request is ${xml}")
            NotFound
        }
      } else {
        logger.warn(s"Unable to find RelatesTo id in [$id]. Request is ${xml}")
        Future.successful(BadRequest)
      }
    }

    val confirmationType: Option[DeliveryStatus] = request.headers.get("x-soap-action").map(d => DeliveryStatus.fromAction(d))

    val id: Option[Node] = (xml \\ "RelatesTo" headOption)
    (confirmationType, id) match {
      case (Some(DeliveryStatus.COE), Some(id)) =>  callService(DeliveryStatus.COE, id.text)
      case (Some(DeliveryStatus.COD), Some(id)) =>  callService(DeliveryStatus.COD, id.text)
      case _ =>
        logger.warn(s"Unable to update message with RelatesTo of [${id}] and x-soap-action value of [${confirmationType}]. Request is ${xml}")
        Future.successful(BadRequest)
    }
  }
}
