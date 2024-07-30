/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformoutboundsoap.connectors

import java.net.URL
import scala.concurrent.Future

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

trait HttpClientV2MockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait BaseHttpClientV2MockModule {
    def aMock: HttpClientV2
    protected def requestBuilderMock: RequestBuilder

    object Post {

      def thenReturn[T](response: T) = {
        when(aMock.post(*)(*)).thenReturn(requestBuilderMock)
        when(requestBuilderMock.withBody(*)(*, *, *)).thenReturn(requestBuilderMock)
        when(requestBuilderMock.withProxy).thenReturn(requestBuilderMock)
        when(requestBuilderMock.execute[T](*, *)).thenReturn(Future.successful(response))
      }

      def thenFail[T]() = {
        when(aMock.post(*)(*)).thenReturn(requestBuilderMock)
        when(requestBuilderMock.withBody(*)(*, *, *)).thenReturn(requestBuilderMock)
        when(requestBuilderMock.execute[T](*, *)).thenReturn(Future.failed(play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException.INSTANCE))
      }

      def verifyUrl(url: URL) = {
        verify(aMock).post(eqTo(url))(*)
      }

      def verifyProxy() = {
        verify(requestBuilderMock, atLeastOnce).withProxy
      }

      def verifyNoProxy() = {
        verify(requestBuilderMock, never).withProxy
      }
    }
  }

  object HttpClientV2Mock extends BaseHttpClientV2MockModule {
    val aMock: HttpClientV2                          = mock[HttpClientV2]
    protected val requestBuilderMock: RequestBuilder = mock[RequestBuilder]
  }
}
