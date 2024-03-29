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

package uk.gov.hmrc.apiplatformoutboundsoap.controllers.actionBuilders

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import _root_.uk.gov.hmrc.http.HttpErrorFunctions

import play.api.Logging
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, Request, Result}

@Singleton
class ValidateConfirmationTypeAction @Inject() ()(implicit ec: ExecutionContext)
    extends ActionFilter[Request] with HttpErrorFunctions with Logging {
  override def executionContext: ExecutionContext = ec

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    val confirmationType: Option[String] = request.headers.get("x-soap-action")
    Future.successful(validateEnum(confirmationType))
  }

  private def validateEnum(deliveryStatus: Option[String]): Option[Status] = {
    val validConfirmationTypeValues    = Seq("CCN2.Service.Platform.AcknowledgementService/CoD", "CCN2.Service.Platform.AcknowledgementService/CoE")
    val deliveryStatusPrepared: String = deliveryStatus.map(d => d.trim).getOrElse("")
    if (validConfirmationTypeValues.contains(deliveryStatusPrepared)) {
      None
    } else {
      logger.warn(s"confirmation type [${deliveryStatus.getOrElse("")}] is not valid. It must be CoD or CoE ")
      Some(BadRequest)
    }
  }
}
