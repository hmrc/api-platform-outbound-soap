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

import java.time.Instant.now
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
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport

import uk.gov.hmrc.apiplatformoutboundsoap.models._

class OutboundMessageRepositoryISpec extends AnyWordSpec with PlayMongoRepositorySupport[OutboundSoapMessage] with Matchers with BeforeAndAfterEach with GuiceOneAppPerSuite
    with IntegrationPatience {
  val repository = app.injector.instanceOf[OutboundMessageRepository]

  override implicit lazy val app: Application = appBuilder.build()
  val ccnHttpStatus: Int                      = 200
  val privateHeaders                          = Some(List(PrivateHeader(name = "name1", value = "value1"), PrivateHeader(name = "name2", value = "value2")))
  val instantNow: Instant                     = now.truncatedTo(ChronoUnit.MILLIS)

  val retryingMessage                     =
    RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", instantNow, instantNow, ccnHttpStatus, None, None, None, None, privateHeaders)
  val sentMessage                         = SentOutboundSoapMessage(randomUUID, "MessageId-A2", "<IE4N03>payload</IE4N03>", "some url", instantNow, ccnHttpStatus, None, None, None, None, privateHeaders)
  implicit val materialiser: Materializer = app.injector.instanceOf[Materializer]
  val failedMessage                       = FailedOutboundSoapMessage(randomUUID, "MessageId-A3", "<IE4N03>payload</IE4N03>", "some url", instantNow, ccnHttpStatus)

  val coeMessage = CoeSoapMessage(
    randomUUID,
    "MessageId-A4",
    "<IE4N03>payload</IE4N03>",
    "some url",
    instantNow,
    ccnHttpStatus,
    coeMessage = Some("<COEMessage><Fault>went wrong</Fault></COEMessage>")
  )
  val codMessage = CodSoapMessage(randomUUID, "MessageId-A5", "<IE4N03>payload</IE4N03>", "some url", instantNow, ccnHttpStatus)

  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  "persist" should {

    "insert a retrying message when it does not exist" in {
      await(repository.persist(retryingMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe retryingMessage
      fetchedRecords.head.status shouldBe SendingStatus.RETRYING
    }

    "insert a sent message when it does not exist" in {
      await(repository.persist(sentMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe sentMessage
      fetchedRecords.head.status shouldBe SendingStatus.SENT
    }

    "insert a failed message when it does not exist" in {
      await(repository.persist(failedMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe failedMessage
      fetchedRecords.head.status shouldBe SendingStatus.FAILED
    }
    "insert a confirmation of exception message when it does not exist" in {
      await(repository.persist(coeMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe coeMessage
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
    }
    "insert a confirmation of delivery message when it does not exist" in {
      await(repository.persist(codMessage))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe codMessage
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
    }

    "message is persisted with TTL" in {
      await(repository.persist(sentMessage))

      val Some(ttlIndex) = await(repository.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "ttlIndex")
      ttlIndex.get("unique") shouldBe None
      ttlIndex.get("background").get shouldBe BsonBoolean(true)
      ttlIndex.get("expireAfterSeconds").get.asNumber().intValue shouldBe 60 * 60 * 24 * 30
    }

    "message is persisted with unique ID" in {
      await(repository.persist(sentMessage))

      val Some(globalIdIndex) = await(repository.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "globalIdIndex")
      globalIdIndex.get("unique") shouldBe Some(BsonBoolean(true))
      globalIdIndex.get("background").get shouldBe BsonBoolean(true)
    }

    "create index on message ID" in {
      await(repository.persist(sentMessage))

      val Some(globalIdIndex) = await(repository.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "messageIdIndex")
      globalIdIndex.get("unique") shouldBe None
      globalIdIndex.get("background").get shouldBe BsonBoolean(true)
    }

    "fail when a message with the same ID already exists" in {
      await(repository.persist(retryingMessage))

      val exception: MongoWriteException = intercept[MongoWriteException] {
        await(repository.persist(retryingMessage))
      }

      exception.getMessage should include("E11000 duplicate key error collection")
    }
  }

  "retrieveMessagesForRetry" should {
    "retrieve retrying messages and ignore sent messages" in {
      await(repository.persist(retryingMessage))
      await(repository.persist(sentMessage))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe retryingMessage
    }

    "not retrieve retrying messages when they are not ready for retrying" in {
      val retryingMessageNotReadyForRetrying = RetryingOutboundSoapMessage(
        randomUUID,
        "MessageId-A1",
        "<IE4N03>payload</IE4N03>",
        "some url",
        instantNow,
        instantNow.plus(Duration.ofHours(1)),
        ccnHttpStatus
      )

      await(repository.persist(retryingMessageNotReadyForRetrying))
      await(repository.persist(sentMessage))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 0
    }

    "retrieve retrying messages with retryDate in ascending order" in {
      val retryingMessageOldRetryDatetime       = retryingMessage.copy(globalId = randomUUID, retryDateTime = retryingMessage.retryDateTime.minus(Duration.ofHours(1)))
      val retryingMessageEvenOlderRetryDatetime = retryingMessage.copy(globalId = randomUUID, retryDateTime = retryingMessage.retryDateTime.minus(Duration.ofHours(2)))
      await(repository.persist(retryingMessageEvenOlderRetryDatetime))
      await(repository.persist(retryingMessage))
      await(repository.persist(retryingMessageOldRetryDatetime))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 3
      fetchedRecords.head shouldBe retryingMessageEvenOlderRetryDatetime
      fetchedRecords(1) shouldBe retryingMessageOldRetryDatetime
      fetchedRecords(2) shouldBe retryingMessage
    }
  }

  "updateNextRetryTime" should {
    "update the retryDateTime on a record given its globalId" in {
      await(repository.persist(retryingMessage))
      val newRetryDateTime = retryingMessage.retryDateTime.minus(Duration.ofHours(2))
      await(repository.updateNextRetryTime(retryingMessage.globalId, newRetryDateTime))

      val fetchedRecords = await(repository.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.retryDateTime shouldBe newRetryDateTime

    }

    "updated message is returned from the database after updating retryDateTime" in {
      await(repository.persist(retryingMessage))
      val newRetryDateTime     = retryingMessage.retryDateTime.minus(Duration.ofHours(2))
      val Some(updatedMessage) = await(repository.updateNextRetryTime(retryingMessage.globalId, newRetryDateTime))

      updatedMessage.retryDateTime shouldBe newRetryDateTime
    }
  }

  "updateStatus" should {
    "update the message to have a status of FAILED" in {
      await(repository.persist(retryingMessage))
      val Some(returnedSoapMessage) = await(repository.updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe SendingStatus.FAILED
      fetchedRecords.head.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
      returnedSoapMessage.status shouldBe SendingStatus.FAILED
      returnedSoapMessage.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
    }

    "update the message to have a status of SENT" in {
      await(repository.persist(retryingMessage))
      val Some(returnedSoapMessage) = await(repository.updateToSent(retryingMessage.globalId, instantNow))

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
      await(repository.persist(sentMessage))
      await(repository.updateConfirmationStatus(sentMessage.messageId, DeliveryStatus.COE, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
      fetchedRecords.head.asInstanceOf[CoeSoapMessage].coeMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "update a message when a CoD is received" in {
      await(repository.persist(sentMessage))
      await(repository.updateConfirmationStatus(sentMessage.messageId, DeliveryStatus.COD, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
      fetchedRecords.head.asInstanceOf[CodSoapMessage].codMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "update all records with the same messageId when a CoE is received" in {
      val secondSentMessage = sentMessage.copy(globalId = randomUUID())
      await(repository.persist(sentMessage))
      await(repository.persist(secondSentMessage))

      await(repository.updateConfirmationStatus(sentMessage.messageId, DeliveryStatus.COE, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 2
      fetchedRecords.head.globalId shouldBe sentMessage.globalId
      fetchedRecords(1).globalId shouldBe secondSentMessage.globalId
      fetchedRecords.head.messageId shouldBe sentMessage.messageId
      fetchedRecords(1).messageId shouldBe secondSentMessage.messageId
      fetchedRecords.head.soapMessage shouldBe sentMessage.soapMessage
      fetchedRecords(1).soapMessage shouldBe secondSentMessage.soapMessage
      fetchedRecords.head.destinationUrl shouldBe sentMessage.destinationUrl
      fetchedRecords(1).destinationUrl shouldBe secondSentMessage.destinationUrl
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
      fetchedRecords(1).status shouldBe DeliveryStatus.COE
      fetchedRecords.head.createDateTime shouldBe sentMessage.createDateTime
      fetchedRecords(1).createDateTime shouldBe secondSentMessage.createDateTime
      fetchedRecords.head.notificationUrl shouldBe sentMessage.notificationUrl
      fetchedRecords(1).notificationUrl shouldBe secondSentMessage.notificationUrl
      fetchedRecords.head.ccnHttpStatus shouldBe sentMessage.ccnHttpStatus
      fetchedRecords(1).ccnHttpStatus shouldBe secondSentMessage.ccnHttpStatus
      fetchedRecords.head.coeMessage shouldBe Some(expectedConfirmationMessageBody)
      fetchedRecords(1).coeMessage shouldBe Some(expectedConfirmationMessageBody)
      fetchedRecords.head.codMessage shouldBe sentMessage.codMessage
      fetchedRecords(1).codMessage shouldBe secondSentMessage.codMessage
      fetchedRecords.head.privateHeaders shouldBe sentMessage.privateHeaders
      fetchedRecords(1).privateHeaders shouldBe secondSentMessage.privateHeaders
    }

    "update all records with the same messageId when a CoD is received" in {
      val secondSentMessage = sentMessage.copy(globalId = randomUUID())
      await(repository.persist(sentMessage))
      await(repository.persist(secondSentMessage))

      await(repository.updateConfirmationStatus(sentMessage.messageId, DeliveryStatus.COD, expectedConfirmationMessageBody))

      val fetchedRecords = await(repository.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 2
      fetchedRecords.head.globalId shouldBe sentMessage.globalId
      fetchedRecords(1).globalId shouldBe secondSentMessage.globalId
      fetchedRecords.head.messageId shouldBe sentMessage.messageId
      fetchedRecords(1).messageId shouldBe secondSentMessage.messageId
      fetchedRecords.head.soapMessage shouldBe sentMessage.soapMessage
      fetchedRecords(1).soapMessage shouldBe secondSentMessage.soapMessage
      fetchedRecords.head.destinationUrl shouldBe sentMessage.destinationUrl
      fetchedRecords(1).destinationUrl shouldBe secondSentMessage.destinationUrl
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
      fetchedRecords(1).status shouldBe DeliveryStatus.COD
      fetchedRecords.head.createDateTime shouldBe sentMessage.createDateTime
      fetchedRecords(1).createDateTime shouldBe secondSentMessage.createDateTime
      fetchedRecords.head.notificationUrl shouldBe sentMessage.notificationUrl
      fetchedRecords(1).notificationUrl shouldBe secondSentMessage.notificationUrl
      fetchedRecords.head.ccnHttpStatus shouldBe sentMessage.ccnHttpStatus
      fetchedRecords(1).ccnHttpStatus shouldBe secondSentMessage.ccnHttpStatus
      fetchedRecords.head.coeMessage shouldBe sentMessage.coeMessage
      fetchedRecords(1).coeMessage shouldBe secondSentMessage.coeMessage
      fetchedRecords.head.codMessage shouldBe Some(expectedConfirmationMessageBody)
      fetchedRecords(1).codMessage shouldBe Some(expectedConfirmationMessageBody)
    }

    "ensure that unknown messageId returns empty option" in {
      val emptyMessage = await(repository.updateConfirmationStatus(sentMessage.messageId, DeliveryStatus.COD, expectedConfirmationMessageBody))
      emptyMessage shouldBe None
    }
  }

  "findById" should {
    "return message when messageId matches" in {
      await(repository.persist(sentMessage))
      val Some(found): Option[OutboundSoapMessage] = await(repository.findById(sentMessage.messageId))
      found shouldBe sentMessage
    }

    "return message when globalId matches" in {
      await(repository.persist(sentMessage))
      val Some(found): Option[OutboundSoapMessage] = await(repository.findById(sentMessage.globalId.toString))
      found shouldBe sentMessage
    }

    "return nothing when ID does not exist" in {
      val found: Option[OutboundSoapMessage] = await(repository.findById(sentMessage.messageId))
      found shouldBe None
    }

    "return newest message for a given messageId" in {
      await(repository.persist(sentMessage))
      await(repository.persist(sentMessage.copy(createDateTime = instantNow.minus(Duration.ofHours(1)), globalId = randomUUID())))

      val Some(found): Option[OutboundSoapMessage] = await(repository.findById(sentMessage.messageId))
      found shouldBe sentMessage
    }
  }
}
