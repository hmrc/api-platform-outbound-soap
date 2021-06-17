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

package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import play.api.libs.json.{JsObject, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.ReadPreference
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.{DeliveryStatus, OutboundSoapMessage, RetryingOutboundSoapMessage, SendingStatus}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.MongoFormatter.{dateFormat, outboundSoapMessageFormatter}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundMessageRepository @Inject()(mongoComponent: ReactiveMongoComponent, appConfig: AppConfig)
                                         (implicit ec: ExecutionContext, m: Materializer)
  extends ReactiveRepository[OutboundSoapMessage, BSONObjectID](
    "messages",
    mongoComponent.mongoConnector.db,
    outboundSoapMessageFormatter,
    ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    Index(key = List("globalId" -> Ascending), name = Some("globalIdIndex"), unique = true, background = true),
    Index(key = List("createDateTime" -> Ascending),
      name = Some("ttlIndex"), background = true, options = BSONDocument("expireAfterSeconds" -> BSONLong(appConfig.retryMessagesTtl.toSeconds)))
  )

  def persist(entity: OutboundSoapMessage)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(entity).map(_ => ())
  }

  def retrieveMessagesForRetry: Source[RetryingOutboundSoapMessage, Future[Any]] = {
    import uk.gov.hmrc.apiplatformoutboundsoap.repositories.MongoFormatter.retryingSoapMessageFormatter

    collection
      .find(Json.obj("status" -> SendingStatus.RETRYING.entryName,
        "retryDateTime" -> Json.obj("$lte" -> now(UTC))), Option.empty[JsObject])
      .sort(Json.obj("retryDateTime" -> 1))
      .cursor[RetryingOutboundSoapMessage](ReadPreference.primaryPreferred)
      .documentSource()
  }

  def updateNextRetryTime(globalId: UUID, newRetryDateTime: DateTime): Future[Option[RetryingOutboundSoapMessage]] = {
    import uk.gov.hmrc.apiplatformoutboundsoap.repositories.MongoFormatter.retryingSoapMessageFormatter

    findAndUpdate(Json.obj("globalId" -> globalId),
      Json.obj("$set" -> Json.obj("retryDateTime" -> newRetryDateTime)), fetchNewObject = true)
      .map(_.result[RetryingOutboundSoapMessage])
  }

  def updateSendingStatus(globalId: UUID, newStatus: SendingStatus): Future[Option[OutboundSoapMessage]] = {
    findAndUpdate(Json.obj("globalId" -> globalId),
      Json.obj("$set" -> Json.obj("status" -> newStatus.entryName)), fetchNewObject = true)
      .map(_.result[OutboundSoapMessage])
  }

  def updateConfirmationStatus(globalId: String, newStatus: DeliveryStatus, confirmationMsg: String): Future[Option[OutboundSoapMessage]] = {
    logger.info(s"conf message is ${confirmationMsg}")
    val field: String = newStatus match {
      case DeliveryStatus.COD => "codMessage"
      case DeliveryStatus.COE => "coeMessage"
    }
    findAndUpdate(Json.obj("globalId" -> globalId),
      Json.obj("$set" -> Json.obj("status" -> newStatus.entryName, field -> confirmationMsg)), fetchNewObject = true)
      .map(_.result[OutboundSoapMessage])
  }

  def findById(globalId: String): Future[Option[OutboundSoapMessage]] = {
    find("globalId" -> JsString(globalId)).map(_.headOption)
      .recover {
        case e: Exception =>
          logger.warn(s"error finding message - ${e.getMessage}")
          None
      }
  }
}
