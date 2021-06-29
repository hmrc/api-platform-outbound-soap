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

import akka.NotUsed
import akka.stream.alpakka.mongodb.scaladsl.MongoSource
import akka.stream.scaladsl.Source
import org.bson.codecs.configuration.CodecRegistries._
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.mongodb.scala.{MongoClient, MongoCollection}
import org.mongodb.scala.ReadPreference.primaryPreferred
import org.mongodb.scala.model.Filters.{and, equal, lte}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.{combine, set}
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, ReturnDocument}
import org.mongodb.scala.result.InsertOneResult
import play.api.Logging
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models.{DeliveryStatus, OutboundSoapMessage, RetryingOutboundSoapMessage, SendingStatus, StatusType}
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.MongoFormatter.outboundSoapMessageFormatter
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundMessageRepository @Inject()(mongoComponent: MongoComponent, appConfig: AppConfig)
                                         (implicit ec: ExecutionContext)
  extends PlayMongoRepository[OutboundSoapMessage](
    collectionName = "messages",
    mongoComponent = mongoComponent,
    domainFormat = outboundSoapMessageFormatter,
    indexes = Seq(IndexModel(ascending("globalId"),
                    IndexOptions().name("globalIdIndex").background(true).unique(true)),
                  IndexModel(ascending("createDateTime"),
                    IndexOptions().name("ttlIndex").background(true)
                      .expireAfter(appConfig.retryMessagesTtl.toSeconds, TimeUnit.SECONDS))))
    with Logging  {

  override lazy val collection: MongoCollection[OutboundSoapMessage] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(MongoFormatter.retryingSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.failedSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.sentSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.codSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.coeSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.dateTimeFormat),
            Codecs.playFormatCodec(StatusType.jsonFormat),
            Codecs.playFormatCodec(DeliveryStatus.jsonFormat),
            Codecs.playFormatCodec(SendingStatus.jsonFormat)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )

  def persist(entity: OutboundSoapMessage): Future[InsertOneResult] = {
    collection.insertOne(entity).toFuture()
  }

  def retrieveMessagesForRetry: Source[RetryingOutboundSoapMessage, NotUsed] = {
    MongoSource(collection.withReadPreference(primaryPreferred)
      .find(filter = and(equal("status", SendingStatus.RETRYING.entryName),
        and(lte("retryDateTime", now(UTC)))))
      .sort(ascending("retryDateTime"))
      .map(_.asInstanceOf[RetryingOutboundSoapMessage]))
  }

  def updateNextRetryTime(globalId: UUID, newRetryDateTime: DateTime): Future[Option[RetryingOutboundSoapMessage]] = {
    collection.withReadPreference(primaryPreferred)
      .findOneAndUpdate(filter = equal("globalId", Codecs.toBson(globalId)),
        update = set("retryDateTime", newRetryDateTime),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      ).map(_.asInstanceOf[RetryingOutboundSoapMessage]).headOption()
  }

  def updateSendingStatus(globalId: UUID, newStatus: SendingStatus): Future[Option[OutboundSoapMessage]] = {
    collection.withReadPreference(primaryPreferred)
      .findOneAndUpdate(filter = equal("globalId", Codecs.toBson(globalId)),
        update = set("status", Codecs.toBson(newStatus.entryName)),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()
  }

  def updateConfirmationStatus(messageId: String, newStatus: DeliveryStatus, confirmationMsg: String): Future[Option[OutboundSoapMessage]] = {
    val field: String = newStatus match {
      case DeliveryStatus.COD => "codMessage"
      case DeliveryStatus.COE => "coeMessage"
    }

    collection.withReadPreference(primaryPreferred)
      .findOneAndUpdate(filter = equal("messageId", messageId),
        update = combine(set("status", Codecs.toBson(newStatus.entryName)), set(field, confirmationMsg)),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER))
      .toFutureOption()
  }

  def findById(messageId: String): Future[Option[OutboundSoapMessage]] = {
    collection.find(filter = equal("messageId", messageId)).headOption()
      .recover {
        case e: Exception =>
          logger.warn(s"error finding message - ${e.getMessage}")
          None
      }
  }
}
