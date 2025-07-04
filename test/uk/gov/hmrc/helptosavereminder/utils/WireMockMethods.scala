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

package uk.gov.hmrc.helptosavereminder.utils

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Writes

import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.chaining.scalaUtilChainingOps

trait WireMockMethods {

  def when(
    method: HTTPMethod,
    uri: String,
    queryParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty,
    body: Option[String] = None
  ): Mapping =
    new Mapping(method, uri, queryParams, headers, body)

  class Mapping(
    method: HTTPMethod,
    uri: String,
    queryParams: Map[String, String],
    headers: Map[String, String],
    body: Option[String]
  ) {
    private val mapping =
      method
        .wireMockMapping(urlPathMatching(uri))
        .withQueryParams(queryParams.view.mapValues(matching).toMap.asJava)
        .pipe(headers.foldLeft(_) { case (m, (key, value)) => m.withHeader(key, equalTo(value)) })
        .pipe { mapping =>
          body match {
            case Some(extractedBody) => mapping.withRequestBody(equalTo(extractedBody))
            case None                => mapping
          }
        }

    def thenReturn[T](status: Int, body: T)(implicit writes: Writes[T]): StubMapping = {
      val stringBody = writes.writes(body).toString()
      thenReturnInternal(status, Map.empty, Some(stringBody))
    }

    def thenReturn(status: Int, body: String): StubMapping =
      thenReturnInternal(status, Map.empty, Some(body))

    def thenReturn(status: Int, headers: Map[String, String] = Map.empty): StubMapping =
      thenReturnInternal(status, headers, None)

    private def thenReturnInternal(status: Int, headers: Map[String, String], body: Option[String]): StubMapping = {
      val response = {
        val statusResponse = aResponse().withStatus(status)
        val responseWithHeaders = headers.foldLeft(statusResponse) { case (res, (key, value)) =>
          res.withHeader(key, value)
        }
        body match {
          case Some(extractedBody) => responseWithHeaders.withBody(extractedBody)
          case None                => responseWithHeaders
        }
      }

      stubFor(mapping.willReturn(response))
    }
  }

  sealed trait HTTPMethod {
    def wireMockMapping(pattern: UrlPattern): MappingBuilder
  }

  case object POST extends HTTPMethod {
    override def wireMockMapping(pattern: UrlPattern): MappingBuilder = post(pattern)
  }

  case object DELETE extends HTTPMethod {
    override def wireMockMapping(pattern: UrlPattern): MappingBuilder = delete(pattern)
  }
}
