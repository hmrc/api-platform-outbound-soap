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

package uk.gov.hmrc.apiplatformoutboundsoap

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}

sealed trait ErrorCode

object ErrorCode {
  case object NOT_FOUND             extends ErrorCode
  case object BAD_REQUEST           extends ErrorCode
  case object INTERNAL_SERVER_ERROR extends ErrorCode

  val values: Set[ErrorCode] = Set(NOT_FOUND, BAD_REQUEST, INTERNAL_SERVER_ERROR)

  def apply(text: String): Option[ErrorCode] = ErrorCode.values.find(_.toString() == text.toUpperCase)

  def unsafeApply(text: String): ErrorCode = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid Error Code"))
}

sealed trait CcnRequestResult

object CcnRequestResult {
  case object UNEXPECTED_SUCCESS extends CcnRequestResult
  case object SUCCESS            extends CcnRequestResult
  case object FAIL_ERROR         extends CcnRequestResult
  case object RETRYABLE_ERROR    extends CcnRequestResult

  val values: Set[CcnRequestResult] = Set(UNEXPECTED_SUCCESS, SUCCESS, FAIL_ERROR, RETRYABLE_ERROR)

  def apply(text: String): Option[CcnRequestResult] = CcnRequestResult.values.find(_.toString() == text.toUpperCase)

  def unsafeApply(text: String): CcnRequestResult = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid CCN Request Result"))
}

object JsErrorResponse {

  def apply(errorCode: ErrorCode, message: JsValueWrapper): JsObject =
    Json.obj(
      "code"    -> errorCode.toString,
      "message" -> message
    )
}
