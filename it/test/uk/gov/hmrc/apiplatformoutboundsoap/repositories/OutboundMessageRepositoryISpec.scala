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

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.UUID.randomUUID

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.ReadPreference.primaryPreferred
import org.mongodb.scala.bson.BsonBoolean
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.mvc.Http.Status
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.apiplatformoutboundsoap.util.TestDataFactory

class OutboundMessageRepositoryISpec extends AnyWordSpec with DefaultPlayMongoRepositorySupport[OutboundSoapMessage] with Matchers with BeforeAndAfterEach with GuiceOneAppPerSuite
    with TestDataFactory
    with IntegrationPatience {
  val repository = app.injector.instanceOf[OutboundMessageRepository]

  override implicit lazy val app: Application = appBuilder.build()
  val ccnHttpStatus: Int                      = 200
  private val instantNow: Instant             = now.truncatedTo(ChronoUnit.MILLIS)

  implicit val materialiser: Materializer = app.injector.instanceOf[Materializer]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  "persist" should {

    "insert a pending message when it does not exist" in {
      await(repository.persist(pendingOutboundSoapMessage))
      val truncCreateDateTime = pendingOutboundSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe pendingOutboundSoapMessage.copy(createDateTime = truncCreateDateTime)
      fetchedRecords.head.status shouldBe SendingStatus.PENDING
    }

    "insert a retrying message when it does not exist" in {
      await(repository.persist(retryingOutboundSoapMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe truncateRetryingMessageInstants(retryingOutboundSoapMessage)
      fetchedRecords.head.status shouldBe SendingStatus.RETRYING
    }

    "insert a sent message when it does not exist" in {
      await(repository.persist(sentOutboundSoapMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe truncateSentMessageInstants(sentOutboundSoapMessage)
      fetchedRecords.head.status shouldBe SendingStatus.SENT
    }

    "insert a failed message when it does not exist" in {
      val messageToPersist = failedOutboundSoapMessage
      await(repository.persist(messageToPersist))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe messageToPersist.copy(createDateTime = messageToPersist.createDateTime.truncatedTo(ChronoUnit.MILLIS))
      fetchedRecords.head.status shouldBe SendingStatus.FAILED
    }

    "insert a confirmation of exception message when it does not exist" in {
      await(repository.persist(coeSoapMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe coeSoapMessage.copy(createDateTime = coeSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS))
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
    }

    "insert a confirmation of delivery message when it does not exist" in {
      await(repository.persist(codSoapMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe codSoapMessage.copy(createDateTime = codSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS))
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
    }

    "message is persisted with TTL" in {
      await(repository.persist(sentOutboundSoapMessage))

      val Some(ttlIndex) = await(repository.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "ttlIndex")
      ttlIndex.get("unique") shouldBe None
      ttlIndex.get("background").get shouldBe BsonBoolean(true)
      ttlIndex.get("expireAfterSeconds").get.asNumber().intValue shouldBe 60 * 60 * 24 * 30
    }

    "message is persisted with unique ID" in {
      await(repository.persist(sentOutboundSoapMessage))

      val Some(globalIdIndex) = await(repository.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "globalIdIndex")
      globalIdIndex.get("unique") shouldBe Some(BsonBoolean(true))
      globalIdIndex.get("background").get shouldBe BsonBoolean(true)
    }

    "create index on message ID" in {
      await(repository.persist(sentOutboundSoapMessage))

      val Some(globalIdIndex) = await(repository.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "messageIdIndex")
      globalIdIndex.get("unique") shouldBe None
      globalIdIndex.get("background").get shouldBe BsonBoolean(true)
    }

    "fail when a message with the same ID already exists" in {
      await(repository.persist(retryingOutboundSoapMessage))

      val exception: MongoWriteException = intercept[MongoWriteException] {
        await(repository.persist(retryingOutboundSoapMessage))
      }

      exception.getMessage should include("E11000 duplicate key error collection")
    }
  }

  "retrieveMessagesForRetry" should {
    "retrieve retrying messages and ignore sent messages" in {
      val retryMessage = retryingOutboundSoapMessage
      await(repository.persist(retryMessage))
      await(repository.persist(sentOutboundSoapMessage))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe truncateRetryingMessageInstants(retryingOutboundSoapMessage)
    }

    "not retrieve retrying messages when they are not ready for retrying" in {
      await(repository.persist(retryingOutboundSoapMessageFutureRetryTime))
      await(repository.persist(sentOutboundSoapMessage))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 0
    }

    "retrieve retrying messages with retryDate in ascending order" in {
      def retryLater(x: RetryingOutboundSoapMessage, y: RetryingOutboundSoapMessage) = {
        x.retryDateTime.isAfter(y.retryDateTime)
      }
      // persist in reverse of expected retrieval order
      val messagesToPersist                                                          = listRetryingOutboundSoapMessage(3).sortWith(retryLater)
      messagesToPersist.map(r => await(repository.persist(r)))
      val expectedRetrievedMessages                                                  = messagesToPersist.map(r => truncateRetryingMessageInstants(r))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 3
      fetchedRecords.head shouldBe expectedRetrievedMessages(2)
      fetchedRecords(1) shouldBe expectedRetrievedMessages(1)
      fetchedRecords(2) shouldBe expectedRetrievedMessages.head
    }
  }

  "updateNextRetryTime" should {
    "update the retryDateTime on a record given its globalId" in {
      await(repository.persist(retryingOutboundSoapMessage))
      val newRetryDateTime = retryingOutboundSoapMessage.retryDateTime.minus(Duration.ofHours(2))
      await(repository.updateNextRetryTime(retryingOutboundSoapMessage.globalId, newRetryDateTime))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.retryDateTime shouldBe newRetryDateTime.truncatedTo(ChronoUnit.MILLIS)

    }

    "updated message is returned from the database after updating retryDateTime" in {
      await(repository.persist(retryingOutboundSoapMessage))
      val newRetryDateTime     = retryingOutboundSoapMessage.retryDateTime.minus(Duration.ofHours(2))
      val Some(updatedMessage) = await(repository.updateNextRetryTime(retryingOutboundSoapMessage.globalId, newRetryDateTime))

      updatedMessage.retryDateTime shouldBe newRetryDateTime.truncatedTo(ChronoUnit.MILLIS)
    }
  }

  "updateStatus" should {
    "update the message to have a status of FAILED" in {
      val expectedHttpResponseCode  = Status.BAD_GATEWAY
      await(repository.persist(retryingOutboundSoapMessage))
      val Some(returnedSoapMessage) = await(repository.updateSendingStatus(retryingOutboundSoapMessage.globalId, SendingStatus.FAILED, expectedHttpResponseCode))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe SendingStatus.FAILED
      fetchedRecords.head.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
      fetchedRecords.head.ccnHttpStatus shouldBe Some(expectedHttpResponseCode)
      returnedSoapMessage.status shouldBe SendingStatus.FAILED
      returnedSoapMessage.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
      returnedSoapMessage.ccnHttpStatus shouldBe Some(expectedHttpResponseCode)
    }

    "update the message to have a status of SENT" in {
      await(repository.persist(retryingOutboundSoapMessage))
      val Some(returnedSoapMessage) = await(repository.updateToSentWhereNotConfirmed(retryingOutboundSoapMessage.globalId, instantNow))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe SendingStatus.SENT
      fetchedRecords.head.isInstanceOf[SentOutboundSoapMessage] shouldBe true
      fetchedRecords.head.sentDateTime shouldBe Some(instantNow)
      returnedSoapMessage.status shouldBe SendingStatus.SENT
      returnedSoapMessage.isInstanceOf[SentOutboundSoapMessage] shouldBe true
      returnedSoapMessage.sentDateTime shouldBe Some(instantNow)
    }
  }

  "updateConfirmationStatus" should {
    val expectedConfirmationMessageBody = "<xml>foobar</xml>"
    "update a message when a CoE is received" in {
      await(repository.persist(sentOutboundSoapMessage))
      await(repository.updateConfirmationStatus(sentOutboundSoapMessage.messageId, DeliveryStatus.COE, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
      fetchedRecords.head.asInstanceOf[CoeSoapMessage].coeMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "update a message when a CoD is received" in {
      await(repository.persist(sentOutboundSoapMessage))
      await(repository.updateConfirmationStatus(sentOutboundSoapMessage.messageId, DeliveryStatus.COD, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
      fetchedRecords.head.asInstanceOf[CodSoapMessage].codMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "not update a message to SENT when a CoD has already been received" in {
      await(repository.persist(codSoapMessage))

      repository.updateToSentWhereNotConfirmed(codSoapMessage.globalId, now)
      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
      fetchedRecords.head.asInstanceOf[CodSoapMessage].codMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "update all records with the same messageId when a CoE is received" in {
      val secondSentMessage = sentOutboundSoapMessage.copy(globalId = randomUUID())
      await(repository.persist(sentOutboundSoapMessage))
      await(repository.persist(secondSentMessage))

      await(repository.updateConfirmationStatus(sentOutboundSoapMessage.messageId, DeliveryStatus.COE, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 2
      fetchedRecords.head.globalId shouldBe sentOutboundSoapMessage.globalId
      fetchedRecords(1).globalId shouldBe secondSentMessage.globalId
      fetchedRecords.head.messageId shouldBe sentOutboundSoapMessage.messageId
      fetchedRecords(1).messageId shouldBe secondSentMessage.messageId
      fetchedRecords.head.soapMessage shouldBe sentOutboundSoapMessage.soapMessage
      fetchedRecords(1).soapMessage shouldBe secondSentMessage.soapMessage
      fetchedRecords.head.destinationUrl shouldBe sentOutboundSoapMessage.destinationUrl
      fetchedRecords(1).destinationUrl shouldBe secondSentMessage.destinationUrl
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
      fetchedRecords(1).status shouldBe DeliveryStatus.COE
      fetchedRecords.head.createDateTime shouldBe sentOutboundSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      fetchedRecords(1).createDateTime shouldBe secondSentMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      fetchedRecords.head.notificationUrl shouldBe sentOutboundSoapMessage.notificationUrl
      fetchedRecords(1).notificationUrl shouldBe secondSentMessage.notificationUrl
      fetchedRecords.head.ccnHttpStatus shouldBe sentOutboundSoapMessage.ccnHttpStatus
      fetchedRecords(1).ccnHttpStatus shouldBe secondSentMessage.ccnHttpStatus
      fetchedRecords.head.coeMessage shouldBe Some(expectedConfirmationMessageBody)
      fetchedRecords(1).coeMessage shouldBe Some(expectedConfirmationMessageBody)
      fetchedRecords.head.codMessage shouldBe sentOutboundSoapMessage.codMessage
      fetchedRecords(1).codMessage shouldBe secondSentMessage.codMessage
      fetchedRecords.head.privateHeaders shouldBe sentOutboundSoapMessage.privateHeaders
      fetchedRecords(1).privateHeaders shouldBe secondSentMessage.privateHeaders
    }

    "update all records with the same messageId when a CoD is received" in {
      val secondSentMessage = sentOutboundSoapMessage.copy(globalId = randomUUID())
      await(repository.persist(sentOutboundSoapMessage))
      await(repository.persist(secondSentMessage))

      await(repository.updateConfirmationStatus(sentOutboundSoapMessage.messageId, DeliveryStatus.COD, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 2
      fetchedRecords.head.globalId shouldBe sentOutboundSoapMessage.globalId
      fetchedRecords(1).globalId shouldBe secondSentMessage.globalId
      fetchedRecords.head.messageId shouldBe sentOutboundSoapMessage.messageId
      fetchedRecords(1).messageId shouldBe secondSentMessage.messageId
      fetchedRecords.head.soapMessage shouldBe sentOutboundSoapMessage.soapMessage
      fetchedRecords(1).soapMessage shouldBe secondSentMessage.soapMessage
      fetchedRecords.head.destinationUrl shouldBe sentOutboundSoapMessage.destinationUrl
      fetchedRecords(1).destinationUrl shouldBe secondSentMessage.destinationUrl
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
      fetchedRecords(1).status shouldBe DeliveryStatus.COD
      fetchedRecords.head.createDateTime shouldBe sentOutboundSoapMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      fetchedRecords(1).createDateTime shouldBe secondSentMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS)
      fetchedRecords.head.notificationUrl shouldBe sentOutboundSoapMessage.notificationUrl
      fetchedRecords(1).notificationUrl shouldBe secondSentMessage.notificationUrl
      fetchedRecords.head.ccnHttpStatus shouldBe sentOutboundSoapMessage.ccnHttpStatus
      fetchedRecords(1).ccnHttpStatus shouldBe secondSentMessage.ccnHttpStatus
      fetchedRecords.head.coeMessage shouldBe sentOutboundSoapMessage.coeMessage
      fetchedRecords(1).coeMessage shouldBe secondSentMessage.coeMessage
      fetchedRecords.head.codMessage shouldBe Some(expectedConfirmationMessageBody)
      fetchedRecords(1).codMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "ensure that unknown messageId returns empty option" in {
      val emptyMessage = await(repository.updateConfirmationStatus(sentOutboundSoapMessage.messageId, DeliveryStatus.COD, expectedConfirmationMessageBody))
      emptyMessage shouldBe None
    }
  }

  "findById" should {
    "return message when messageId matches" in {
      val messageToPersist                         = sentOutboundSoapMessage
      await(repository.persist(messageToPersist))
      val Some(found): Option[OutboundSoapMessage] = await(repository.findById(messageToPersist.messageId))
      found shouldBe truncateSentMessageInstants(messageToPersist)
    }

    "return message when globalId matches" in {
      await(repository.persist(sentOutboundSoapMessage))
      val Some(found): Option[OutboundSoapMessage] = await(repository.findById(sentOutboundSoapMessage.globalId.toString))
      found shouldBe truncateSentMessageInstants(sentOutboundSoapMessage)
    }

    "return nothing when ID does not exist" in {
      val found: Option[OutboundSoapMessage] = await(repository.findById(sentOutboundSoapMessage.messageId))
      found shouldBe None
    }

    "return newest message for a given messageId" in {
      await(repository.persist(sentOutboundSoapMessage))
      await(repository.persist(sentOutboundSoapMessage.copy(createDateTime = instantNow.minus(Duration.ofHours(1)), globalId = randomUUID())))

      val Some(found): Option[OutboundSoapMessage] = await(repository.findById(sentOutboundSoapMessage.messageId))
      found shouldBe truncateSentMessageInstants(sentOutboundSoapMessage)
    }
  }

  private def truncateSentMessageInstants(sentMessage: SentOutboundSoapMessage) = {
    sentMessage.copy(
      createDateTime = sentMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS),
      sentDateTime = Some(sentMessage.sentDateTime.get.truncatedTo(ChronoUnit.MILLIS))
    )
  }

  private def truncateRetryingMessageInstants(retryingMessage: RetryingOutboundSoapMessage) = {
    retryingMessage.copy(
      createDateTime = retryingMessage.createDateTime.truncatedTo(ChronoUnit.MILLIS),
      retryDateTime = retryingMessage.retryDateTime.truncatedTo(ChronoUnit.MILLIS)
    )
  }

}
