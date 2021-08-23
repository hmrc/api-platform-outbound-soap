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

package uk.gov.hmrc.apiplatformoutboundsoap.http

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.http.HttpVerbs
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.{WSClient, WSProxyServer}
import uk.gov.hmrc.http.hooks.HookData
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.http.ws.{WSProxy, WSProxyConfiguration}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class TwoWaySSLProxyClient (
                       httpAuditing: HttpAuditing,
                       config: Configuration,
                       wsClient: WSClient,
                       proxyConfigPath: String,
                       actorSystem: ActorSystem)
  extends DefaultHttpClient(config, httpAuditing, wsClient, actorSystem) with WSProxy with HttpVerbs {

  override lazy val wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration(proxyConfigPath, config)

  private class RawHttpReads extends HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  // this HttpReads instance for HttpResponse is preferred over the default
  // uk.gov.hmrc.http.RawReads.readRaw as this custom one doesn't throw exceptions
  private val rawHttpReads = new RawHttpReads

  def get(
           url: String,
           queryParams: Seq[(String, String)] = Seq.empty,
           headers: Map[String, String] = Map.empty[String, String])(
           implicit hc: HeaderCarrier,
           ec: ExecutionContext): Future[HttpResponse] =
    GET(url, queryParams, headers.toSeq)(rawHttpReads, hc, ec)

  def post[A](url: String, body: A, headers: Map[String, String] = Map.empty[String, String])(
    implicit w: Writes[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    POST(url, body, headers.toSeq)(w, rawHttpReads, hc, ec)

  def put[A](
              url: String,
              body: A,
              headers: Map[String, String] = Map.empty[String, String],
              needsAuditing: Boolean = true)(
              implicit w: Writes[A],
              hc: HeaderCarrier,
              ec: ExecutionContext): Future[HttpResponse] =
    withTracing(PUT, url) {
      val httpResponse = doPut(url, body, headers.toSeq)(w, ec)
      if (needsAuditing) {
        executeHooks(PUT, new URL(url), headers.toSeq, Option(HookData.FromString(Json.stringify(w.writes(body)))), httpResponse)
      }
      mapErrors(PUT, url, httpResponse).map(response ⇒ rawHttpReads.read(PUT, url, response))
    }

}
