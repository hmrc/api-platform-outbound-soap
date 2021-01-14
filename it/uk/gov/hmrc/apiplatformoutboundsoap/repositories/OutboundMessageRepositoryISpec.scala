package uk.gov.hmrc.apiplatformoutboundsoap.repositories

import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.api.ReadPreference
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.apiplatformoutboundsoap.models.{OutboundSoapMessage, SendingStatus}
import uk.gov.hmrc.mongo.RepositoryPreparation

import java.util.UUID

class OutboundMessageRepositoryISpec extends AnyWordSpec with Matchers with RepositoryPreparation with BeforeAndAfterEach with GuiceOneAppPerSuite  {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override implicit lazy val app: Application = appBuilder.build()
  val repo: OutboundMessageRepository = app.injector.instanceOf[OutboundMessageRepository]

  override def beforeEach(): Unit = {
    prepare(repo)
  }

  "persist" should {
    val message = OutboundSoapMessage(UUID.randomUUID(), Some("MessageId-A1"), "<IE4N03>payload</IE4N03>", SendingStatus.SENT, DateTime.now(UTC))

    "insert a message when it does not exist" in {
      await(repo.persist(message))

      val fetchedRecords = await(repo.findAll(ReadPreference.primaryPreferred))
      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldBe message
    }

    "fail when a message with the same ID already exists" in {
      await(repo.persist(message))

      val exception: DatabaseException = intercept[DatabaseException] {
        await(repo.persist(message))
      }

      exception.getMessage should include ("E11000 duplicate key error collection")
    }
  }
}
