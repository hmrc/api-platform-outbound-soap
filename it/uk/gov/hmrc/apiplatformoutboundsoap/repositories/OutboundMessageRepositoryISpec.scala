package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.ReadPreference
import reactivemongo.bson.BSONLong
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.apiplatformoutboundsoap.models.{FailedOutboundSoapMessage, RetryingOutboundSoapMessage, SendingStatus, SentOutboundSoapMessage}
import uk.gov.hmrc.mongo.RepositoryPreparation

import java.util.UUID.randomUUID

class OutboundMessageRepositoryISpec extends AnyWordSpec with Matchers with RepositoryPreparation with BeforeAndAfterEach with GuiceOneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()
  val repo: OutboundMessageRepository = app.injector.instanceOf[OutboundMessageRepository]
  val ccnHttpStatus: Int = 200
  implicit val materialiser: Materializer = app.injector.instanceOf[Materializer]

  override def beforeEach(): Unit = {
    prepare(repo)
  }

  val retryingMessage = RetryingOutboundSoapMessage(randomUUID, Some("MessageId-A1"), "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), DateTime.now(UTC), ccnHttpStatus)
  val sentMessage = SentOutboundSoapMessage(randomUUID, Some("MessageId-A2"), "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), ccnHttpStatus)
  val failedMessage = FailedOutboundSoapMessage(randomUUID, Some("MessageId-A3"), "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC), ccnHttpStatus)
  "persist" should {

    "insert a retrying message when it does not exist" in {
      await(repo.persist(retryingMessage))

      val fetchedRecords = await(repo.findAll(ReadPreference.primaryPreferred))
      val Some(jsonRecord) = await(repo.collection.find(Json.obj(), Option.empty[JsObject]).one[JsObject])
      (jsonRecord \ "status").as[String] shouldBe "RETRYING"


      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe retryingMessage
    }

    "insert a sent message when it does not exist" in {
      await(repo.persist(sentMessage))

      val fetchedRecords = await(repo.findAll(ReadPreference.primaryPreferred))
      val Some(jsonRecord) = await(repo.collection.find(Json.obj(), Option.empty[JsObject]).one[JsObject])
      (jsonRecord \ "status").as[String] shouldBe "SENT"

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe sentMessage
    }

    "insert a failed message when it does not exist" in {
      await(repo.persist(failedMessage))

      val fetchedRecords = await(repo.findAll(ReadPreference.primaryPreferred))
      val Some(jsonRecord) = await(repo.collection.find(Json.obj(), Option.empty[JsObject]).one[JsObject])
      (jsonRecord \ "status").as[String] shouldBe "FAILED"

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe failedMessage
    }

    "message is persisted with TTL" in {
      await(repo.persist(sentMessage))

      val Some(ttlIndex) = await(repo.collection.indexesManager.list()).find(i => i.name.contains("ttlIndex"))
      ttlIndex.unique shouldBe false
      ttlIndex.background shouldBe true
      ttlIndex.options.get("expireAfterSeconds") shouldBe Some(BSONLong(60 * 60 * 24 * 30))
    }

    "message is persisted with unique ID" in {
      await(repo.persist(sentMessage))

      val Some(globalIdIndex) = await(repo.collection.indexesManager.list()).find(i => i.name.contains("globalIdIndex"))
      globalIdIndex.background shouldBe true
      globalIdIndex.unique shouldBe true
    }

    "fail when a message with the same ID already exists" in {
      await(repo.persist(retryingMessage))

      val exception: DatabaseException = intercept[DatabaseException] {
        await(repo.persist(retryingMessage))
      }

      exception.getMessage should include("E11000 duplicate key error collection")
    }
  }

  "retrieveMessagesForRetry" should {
    "retrieve retrying messages and ignore sent messages" in {
      await(repo.persist(retryingMessage))
      await(repo.persist(sentMessage))

      val fetchedRecords = await(repo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe retryingMessage
    }

    "not retrieve retrying messages when they are not ready for retrying" in {
      val retryingMessageNotReadyForRetrying = RetryingOutboundSoapMessage(
        randomUUID, Some("MessageId-A1"), "<IE4N03>payload</IE4N03>", "some url", DateTime.now(UTC),
        DateTime.now(UTC).plusHours(1), ccnHttpStatus)

      await(repo.persist(retryingMessageNotReadyForRetrying))
      await(repo.persist(sentMessage))

      val fetchedRecords = await(repo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 0
    }

    "retrieve retrying messages with retryDate in ascending order" in {
      val retryingMessageOldRetryDatetime = retryingMessage.copy(globalId = randomUUID, retryDateTime = retryingMessage.retryDateTime.minusHours(1))
      val retryingMessageEvenOlderRetryDatetime = retryingMessage.copy(globalId = randomUUID, retryDateTime = retryingMessage.retryDateTime.minusHours(2))
      await(repo.persist(retryingMessageEvenOlderRetryDatetime))
      await(repo.persist(retryingMessage))
      await(repo.persist(retryingMessageOldRetryDatetime))

      val fetchedRecords = await(repo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 3
      fetchedRecords.head shouldBe retryingMessageEvenOlderRetryDatetime
      fetchedRecords(1) shouldBe retryingMessageOldRetryDatetime
      fetchedRecords(2) shouldBe retryingMessage
    }
  }

  "updateNextRetryTime" should {
    "update the retryDateTime on a record given its globalID" in {
      await(repo.persist(retryingMessage))
      val newRetryDateTime = retryingMessage.retryDateTime.minusHours(2)
      await(repo.updateNextRetryTime(retryingMessage.globalId, newRetryDateTime))

      val fetchedRecords = await(repo.retrieveMessagesForRetry.runWith(Sink.seq[RetryingOutboundSoapMessage]))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.retryDateTime shouldBe newRetryDateTime

    }

    "updated message is returned from the database after updating retryDateTime" in {
      await(repo.persist(retryingMessage))
      val newRetryDateTime = retryingMessage.retryDateTime.minusHours(2)
      val Some(updatedMessage) = await(repo.updateNextRetryTime(retryingMessage.globalId, newRetryDateTime))

      updatedMessage.retryDateTime shouldBe newRetryDateTime
    }
  }

  "updateStatus" should {
    "update the message to have a status of FAILED" in {
      await(repo.persist(retryingMessage))
      val Some(returnedSoapMessage) = await(repo.updateStatus(retryingMessage.globalId, SendingStatus.FAILED))

      val fetchedRecords = await(repo.findAll(ReadPreference.primaryPreferred))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe SendingStatus.FAILED
      fetchedRecords.head.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
      returnedSoapMessage.status shouldBe SendingStatus.FAILED
      returnedSoapMessage.isInstanceOf[FailedOutboundSoapMessage] shouldBe true
    }

    "update the message to have a status of SENT" in {
      await(repo.persist(retryingMessage))
      val Some(returnedSoapMessage) = await(repo.updateStatus(retryingMessage.globalId, SendingStatus.SENT))

      val fetchedRecords = await(repo.findAll(ReadPreference.primaryPreferred))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head.status shouldBe SendingStatus.SENT
      fetchedRecords.head.isInstanceOf[SentOutboundSoapMessage] shouldBe true
      returnedSoapMessage.status shouldBe SendingStatus.SENT
      returnedSoapMessage.isInstanceOf[SentOutboundSoapMessage] shouldBe true
    }
  }
}
