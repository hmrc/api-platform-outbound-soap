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

import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.apiplatformoutboundsoap.models.OutboundSoapMessage
import uk.gov.hmrc.apiplatformoutboundsoap.repositories.MongoFormatter.outboundSoapMessageFormatter
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutboundMessageRepository @Inject()(mongoComponent: ReactiveMongoComponent)
                                         (implicit ec: ExecutionContext)
  extends ReactiveRepository[OutboundSoapMessage, BSONObjectID](
    "messages",
    mongoComponent.mongoConnector.db,
    outboundSoapMessageFormatter,
    ReactiveMongoFormats.objectIdFormats) {

  override def indexes = Seq(
    Index(key = List("globalId" -> Ascending), name = Some("globalIdIndex"), unique = true, background = true)
  )

  def persist(entity: OutboundSoapMessage)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(entity).map(_ => ())
  }
}
