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

package uk.gov.hmrc.apiplatformoutboundsoap.scheduled

import akka.Done
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatformoutboundsoap.config.AppConfig
import uk.gov.hmrc.apiplatformoutboundsoap.services.OutboundService
import uk.gov.hmrc.lock.LockRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.Duration


class SoapMessageRetryJobSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar {

  trait Setup {
    val appConfigMock: AppConfig = mock[AppConfig]
    when(appConfigMock.retryJobLockDuration).thenReturn(Duration("1 hour"))
    val outboundServiceMock: OutboundService = mock[OutboundService]
    val repositoryMock: LockRepository = mock[LockRepository]
    val underTest: SoapMessageRetryJob = new SoapMessageRetryJob(appConfigMock, repositoryMock, outboundServiceMock)
  }

  "executeInLock" should {
    "retry messages successfully" in new Setup {
      when(outboundServiceMock.retryMessages(*)).thenReturn(successful(Done))
      val result: underTest.Result = await(underTest.executeInLock)

      result.message shouldBe "Done"
      verify(outboundServiceMock).retryMessages(*)
    }

    "retry messages with an exception which is propagated" in new Setup {
      when(outboundServiceMock.retryMessages(*)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val ex: RuntimeException = intercept[RuntimeException](await(underTest.executeInLock))

      ex.getMessage shouldBe "Something went wrong"
    }

  }

}
