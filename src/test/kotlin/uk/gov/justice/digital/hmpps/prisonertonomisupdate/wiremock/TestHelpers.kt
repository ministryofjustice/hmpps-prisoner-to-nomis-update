package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.junit.jupiter.api.Assertions
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, pattern: StringValuePattern): RequestPatternBuilder = this.withRequestBody(matchingJsonPath(path, pattern))
fun MappingBuilder.withRequestBodyJsonPath(path: String, pattern: StringValuePattern): MappingBuilder = this.withRequestBody(matchingJsonPath(path, pattern))

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, equalTo: Any): RequestPatternBuilder = this.withRequestBodyJsonPath(path, equalTo(equalTo.toString()))

fun WireMockServer.getRequestBodyAsString(pattern: RequestPatternBuilder): String {
  val allServerEvents = findAll(pattern)
  val request = allServerEvents.lastOrNull() ?: throw Assertions.fail("No matching request found")
  return request.bodyAsString
}

fun WireMockServer.getRequestBodiesAsString(pattern: RequestPatternBuilder): List<String> = findAll(pattern).map { it.bodyAsString }

inline fun <reified T> WireMockServer.getRequestBody(pattern: RequestPatternBuilder, jsonMapper: JsonMapper): T = getRequestBodyAsString(pattern).let {
  return jsonMapper.readValue<T>(it)
}

inline fun <reified T> WireMockServer.getRequestBodies(pattern: RequestPatternBuilder, jsonMapper: JsonMapper): List<T> = getRequestBodiesAsString(pattern).map {
  jsonMapper.readValue<T>(it)
}
