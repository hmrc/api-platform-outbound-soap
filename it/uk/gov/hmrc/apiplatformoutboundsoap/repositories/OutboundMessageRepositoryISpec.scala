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
import akka.stream.scaladsl.Sink
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.ReadPreference.primaryPreferred
import org.mongodb.scala.bson.{BsonBoolean, BsonInt64}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.apiplatformoutboundsoap.models._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport

import java.util.UUID.randomUUID

class OutboundMessageRepositoryISpec extends AnyWordSpec with PlayMongoRepositorySupport[OutboundSoapMessage] with
  Matchers with BeforeAndAfterEach with GuiceOneAppPerSuite {
  val serviceRepo = repository.asInstanceOf[OutboundMessageRepository]

  override implicit lazy val app: Application = appBuilder.build()
  val ccnHttpStatus: Int = 200
  val retryingMessage = RetryingOutboundSoapMessage(randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url",
    DateTime.now(UTC), DateTime.now(UTC), ccnHttpStatus)
  val sentMessage = SentOutboundSoapMessage(randomUUID, "MessageId-A2", "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), ccnHttpStatus)
  implicit val materialiser: Materializer = app.injector.instanceOf[Materializer]
  val failedMessage = FailedOutboundSoapMessage(randomUUID, "MessageId-A3", "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), ccnHttpStatus)
  val coeMessage = CoeSoapMessage(randomUUID, "MessageId-A4", "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), ccnHttpStatus)
  val codMessage = CodSoapMessage(randomUUID, "MessageId-A5", "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), ccnHttpStatus)

  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override protected def repository: PlayMongoRepository[OutboundSoapMessage] = app.injector.instanceOf[OutboundMessageRepository]

  "persist" should {

    "insert a retrying message when it does not exist" in {
      await(serviceRepo.persist(retryingMessage))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe retryingMessage
      fetchedRecords.head.status shouldBe SendingStatus.RETRYING
    }

    "insert a sent message when it does not exist" in {
      await(serviceRepo.persist(sentMessage))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe sentMessage
      fetchedRecords.head.status shouldBe SendingStatus.SENT
    }

    "insert a failed message when it does not exist" in {
      await(serviceRepo.persist(failedMessage))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe failedMessage
      fetchedRecords.head.status shouldBe SendingStatus.FAILED
    }
    "insert a confirmation of exception message when it does not exist" in {
      await(serviceRepo.persist(coeMessage))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe coeMessage
      fetchedRecords.head.status shouldBe DeliveryStatus.COE
    }
    "insert a confirmation of delivery message when it does not exist" in {
      await(serviceRepo.persist(codMessage))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe codMessage
      fetchedRecords.head.status shouldBe DeliveryStatus.COD
    }

    "message is persisted with TTL" in {
      await(serviceRepo.persist(sentMessage))

      val Some(ttlIndex) = await(serviceRepo.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "ttlIndex")
      ttlIndex.get("unique") shouldBe None
      ttlIndex.get("background").get shouldBe BsonBoolean(true)
      ttlIndex.get("expireAfterSeconds") shouldBe Some(BsonInt64(60 * 60 * 24 * 30))
    }

    "message is persisted with unique ID" in {
      await(serviceRepo.persist(sentMessage))

      val Some(globalIdIndex) = await(serviceRepo.collection.listIndexes().toFuture()).find(i => i.get("name").get.asString().getValue == "globalIdIndex")
      globalIdIndex.get("unique") shouldBe Some(BsonBoolean(true))
      globalIdIndex.get("background").get shouldBe BsonBoolean(true)
    }

    "fail when a message with the same ID already exists" in {
      await(serviceRepo.persist(retryingMessage))

      val exception: MongoWriteException = intercept[MongoWriteException] {
        await(serviceRepo.persist(retryingMessage))
      }

      exception.getMessage should include("E11000 duplicate key error collection")
    }
  }

    "retrieveMessagesForRetry" should {
      "retrieve retrying messages and ignore sent messages" in {
        await(serviceRepo.persist(retryingMessage))
        await(serviceRepo.persist(sentMessage))

        val fetchedRecords = await(serviceRepo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
        fetchedRecords.size shouldBe 1
        fetchedRecords.head shouldBe retryingMessage
      }

      "not retrieve retrying messages when they are not ready for retrying" in {
        val retryingMessageNotReadyForRetrying = RetryingOutboundSoapMessage(
          randomUUID, "MessageId-A1", "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC),
          DateTime.now(UTC).plusHours(1), ccnHttpStatus)

        await(serviceRepo.persist(retryingMessageNotReadyForRetrying))
        await(serviceRepo.persist(sentMessage))

        val fetchedRecords = await(serviceRepo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
        fetchedRecords.size shouldBe 0
      }

      "retrieve retrying messages with retryDate in ascending order" in {
        val retryingMessageOldRetryDatetime = retryingMessage.copy(globalId = randomUUID, retryDateTime = retryingMessage.retryDateTime.minusHours(1))
        val retryingMessageEvenOlderRetryDatetime = retryingMessage.copy(globalId = randomUUID, retryDateTime = retryingMessage.retryDateTime.minusHours(2))
        await(serviceRepo.persist(retryingMessageEvenOlderRetryDatetime))
        await(serviceRepo.persist(retryingMessage))
        await(serviceRepo.persist(retryingMessageOldRetryDatetime))

        val fetchedRecords = await(serviceRepo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
        fetchedRecords.size shouldBe 3
        fetchedRecords.head shouldBe retryingMessageEvenOlderRetryDatetime
        fetchedRecords(1) shouldBe retryingMessageOldRetryDatetime
        fetchedRecords(2) shouldBe retryingMessage
      }
    }

    "updateNextRetryTime" should {
      "update the retryDateTime on a record given its globalID" in {
        await(serviceRepo.persist(retryingMessage))
        val newRetryDateTime = retryingMessage.retryDateTime.minusHours(2)
        await(serviceRepo.updateNextRetryTime(retryingMessage.globalId, newRetryDateTime))

        val fetchedRecords = await(serviceRepo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
        fetchedRecords.size shouldBe 1
        fetchedRecords.head.retryDateTime shouldBe newRetryDateTime

      }

      "updated message is returned from the database after updating retryDateTime" in {
        await(serviceRepo.persist(retryingMessage))
        val newRetryDateTime = retryingMessage.retryDateTime.minusHours(2)
        val Some(updatedMessage) = await(serviceRepo.updateNextRetryTime(retryingMessage.globalId, newRetryDateTime))

        updatedMessage.retryDateTime shouldBe newRetryDateTime
      }
    }

    "updateStatus" should {
      "update the message to have a status of FAILED" in {
        await(serviceRepo.persist(retryingMessage))
        val Some(returnedSoapMessage) = await(serviceRepo.updateSendingStatus(retryingMessage.globalId, SendingStatus.FAILED))

        val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find.toFuture())
        fetchedRecords.size shouldBe 1
        fetchedRecords.head.status shouldBe SendingStatus.FAILED
        fetchedRecords.head.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
        returnedSoapMessage.status shouldBe SendingStatus.FAILED
        returnedSoapMessage.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
      }

      "update the message to have a status of SENT" in {
        await(serviceRepo.persist(retryingMessage))
        val Some(returnedSoapMessage) = await(serviceRepo.updateSendingStatus(retryingMessage.globalId, SendingStatus.SENT))

        val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find.toFuture())
        fetchedRecords.size shouldBe 1
        fetchedRecords.head.status shouldBe SendingStatus.SENT
        fetchedRecords.head.isInstanceOf[SentOutboundSoapMessage] shouldBe true
        returnedSoapMessage.status shouldBe SendingStatus.SENT
        returnedSoapMessage.isInstanceOf[SentOutboundSoapMessage] shouldBe true
      }
    }

    "updateConfirmationStatus" should {
      val expectedConfirmationMessageBody = "<xml>foobar</xml>"
      "update a message when a CoE is received" in {
        await(serviceRepo.persist(sentMessage))
        await(serviceRepo.updateConfirmationStatus(sentMessage.globalId.toString, DeliveryStatus.COE, expectedConfirmationMessageBody))

        val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find.toFuture())
        fetchedRecords.size shouldBe 1
        fetchedRecords.head.status shouldBe DeliveryStatus.COE
        fetchedRecords.head.asInstanceOf[CoeSoapMessage].coeMessage shouldBe Some(expectedConfirmationMessageBody)
      }

      "update a message when a CoD is received" in {
        await(serviceRepo.persist(sentMessage))
        await(serviceRepo.updateConfirmationStatus(sentMessage.globalId.toString, DeliveryStatus.COD, expectedConfirmationMessageBody))

        val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred).find.toFuture())

        fetchedRecords.size shouldBe 1
        fetchedRecords.head.status shouldBe DeliveryStatus.COD
        fetchedRecords.head.asInstanceOf[CodSoapMessage].codMessage shouldBe Some(expectedConfirmationMessageBody)
      }
    }

    "findById" should {
      "return message when ID exists" in {
        await(serviceRepo.persist(sentMessage))
        val found: Option[OutboundSoapMessage] = await(serviceRepo.findById(sentMessage.messageId))
        found shouldBe Some(sentMessage)
      }

      "return nothing when ID does not exist" in {
        val found: Option[OutboundSoapMessage] = await(serviceRepo.findById(sentMessage.messageId))
        found shouldBe None
      }
    }
  }

