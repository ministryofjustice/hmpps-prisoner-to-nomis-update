package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

val log: Logger = LoggerFactory.getLogger("HelperApi")

suspend fun <T> doApiCallWithRetries(api: suspend () -> T) = runCatching {
  api()
}.recoverCatching {
  log.warn("Retrying API call 1", it)
  api()
}.recoverCatching {
  log.warn("Retrying API call 2", it)
  api()
}.getOrThrow()

class RestResponsePage<T>(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER")
  @JsonProperty("pageable")
  pageable: JsonNode,
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
