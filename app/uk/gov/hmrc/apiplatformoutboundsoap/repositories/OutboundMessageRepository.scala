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

package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.connectors.mongodb.scaladsl.MongoSource
import org.apache.pekko.stream.scaladsl.Source
import org.bson.codecs.configuration.CodecRegistries._
import org.mongodb.scala.ReadPreference.primaryPreferred
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, equal, lte, or}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Sorts.descending
import org.mongodb.scala.model.Updates.{combine, set}
import org.mongodb.scala.model._
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.{MongoClient, MongoCollection}

import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.MongoFormatter.outboundSoapMessageFormatter

@Singleton
class OutboundMessageRepository @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[OutboundSoapMessage](
      collectionName = "messages",
      mongoComponent = mongoComponent,
      domainFormat = outboundSoapMessageFormatter,
      indexes = Seq(
        IndexModel(ascending("globalId"), IndexOptions().name("globalIdIndex").background(true).unique(true)),
        IndexModel(ascending("messageId"), IndexOptions().name("messageIdIndex").background(true).unique(false)),
        IndexModel(ascending("retryDateTime"), IndexOptions().name("retryDateTimeIndex").background(true).unique(false)),
        IndexModel(
          ascending("createDateTime"),
          IndexOptions().name("ttlIndex").background(true)
            .expireAfter(appConfig.retryMessagesTtl.toSeconds, TimeUnit.SECONDS)
        )
      )
    )
    with Logging with MongoJavatimeFormats.Implicits {

  override lazy val collection: MongoCollection[OutboundSoapMessage] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(MongoFormatter.pendingSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.retryingSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.failedSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.sentSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.codSoapMessageFormatter),
            Codecs.playFormatCodec(MongoFormatter.coeSoapMessageFormatter),
            Codecs.playFormatCodec(StatusType.format),
            Codecs.playFormatCodec(DeliveryStatus.format),
            Codecs.playFormatCodec(SendingStatus.format)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )

  def persist(entity: OutboundSoapMessage): Future[InsertOneResult] = {
    collection.insertOne(entity).toFuture()
  }

  def retrieveMessagesForRetry: Source[RetryingOutboundSoapMessage, NotUsed] = {
    MongoSource(collection.withReadPreference(primaryPreferred())
      .find(filter = and(equal("status", SendingStatus.RETRYING.toString), and(lte("retryDateTime", Codecs.toBson(Instant.now)))))
      .sort(ascending("retryDateTime"))
      .map(_.asInstanceOf[RetryingOutboundSoapMessage]))
  }

  def updateNextRetryTime(globalId: UUID, newRetryDateTime: Instant): Future[Option[RetryingOutboundSoapMessage]] = {
    collection.withReadPreference(primaryPreferred())
      .findOneAndUpdate(
        filter = equal("globalId", Codecs.toBson(globalId)),
        update = set("retryDateTime", Codecs.toBson(newRetryDateTime)),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      ).map(_.asInstanceOf[RetryingOutboundSoapMessage]).headOption()
  }

  def updateSendingStatus(globalId: UUID, newStatus: SendingStatus, responseCode: Int = 0): Future[Option[OutboundSoapMessage]] = {
    collection.withReadPreference(primaryPreferred())
      .findOneAndUpdate(
        filter = equal("globalId", Codecs.toBson(globalId)),
        update = combine(set("status", Codecs.toBson(newStatus.toString)), set("ccnHttpStatus", Codecs.toBson(responseCode))),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()
  }

   def updateSendingStatusWithRetryDateTime(globalId: UUID, newStatus: SendingStatus, responseCode: Int = 0, retryDateTime: Instant): Future[Option[OutboundSoapMessage]] = {
    collection.withReadPreference(primaryPreferred())
      .findOneAndUpdate(
        filter = equal("globalId", Codecs.toBson(globalId)),
        update = combine(set("status", Codecs.toBson(newStatus.toString)), set("ccnHttpStatus", Codecs.toBson(responseCode)), set("retryDateTime", Codecs.toBson(retryDateTime))),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()
  }

  private def updateToSent(globalId: UUID, sentInstant: Instant): Future[Option[OutboundSoapMessage]] = {
    collection.withReadPreference(primaryPreferred())
      .findOneAndUpdate(
        filter = equal("globalId", Codecs.toBson(globalId)),
        update = combine(
          set("sentDateTime", Codecs.toBson(sentInstant)),
          set("status", Codecs.toBson(SendingStatus.SENT.toString))
        ),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()
  }

  def updateToSentWhereNotConfirmed(globalId: UUID, sentInstant: Instant): Future[Option[OutboundSoapMessage]] = {
    val maybeUpdateCandidate = findById(globalId.toString)
    maybeUpdateCandidate.flatMap(updateCandidate =>
      updateCandidate.map(m =>
        m.status match {
          case DeliveryStatus.COE =>
            logger.warn(s"CoE already received for message with globalId ${m.globalId} so not updating to SENT")
            maybeUpdateCandidate
          case DeliveryStatus.COD =>
            logger.warn(s"CoD already received for message with globalId ${m.globalId} so not updating to SENT")
            maybeUpdateCandidate
          case _                  => updateToSent(globalId, sentInstant)
        }
      ).head
    )
  }

  def updateConfirmationStatus(messageId: String, newStatus: DeliveryStatus, confirmationMsg: String): Future[Option[OutboundSoapMessage]] = {
    val field: String = newStatus match {
      case DeliveryStatus.COD => "codMessage"
      case DeliveryStatus.COE => "coeMessage"
    }

    for {
      _           <- collection.bulkWrite(
                       List(UpdateManyModel(Document("messageId" -> messageId), combine(set("status", Codecs.toBson(newStatus.toString)), set(field, confirmationMsg)))),
                       BulkWriteOptions().ordered(false)
                     ).toFuture()
      findUpdated <- findById(messageId)
    } yield findUpdated
  }

  def findById(searchForId: String): Future[Option[OutboundSoapMessage]] = {
    val findQuery = or(Document("messageId" -> searchForId), Document("globalId" -> searchForId))
    collection.find(findQuery).sort(descending("createDateTime")).headOption()
      .recover {
        case e: Exception =>
          logger.warn(s"error finding message - ${e.getMessage}")
          None
      }
  }
}
