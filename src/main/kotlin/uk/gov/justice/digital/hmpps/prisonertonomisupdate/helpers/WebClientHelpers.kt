package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNotFound(): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .awaitSingleOrNull()

suspend fun WebClient.ResponseSpec.awaitBodilessEntityOrThrowOnConflict() =
  this.toBodilessEntity()
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.error(DuplicateMappingException(it.getResponseBodyAs(DuplicateErrorResponse::class.java)!!))
    }
    .awaitSingleOrNull()

class DuplicateMappingException(val error: DuplicateErrorResponse) : RuntimeException("message")

class DuplicateErrorResponse(
  val moreInfo: DuplicateErrorContent,
)

data class DuplicateErrorContent(
  val duplicate: Map<String, *>,
  val existing: Map<String, *>? = null,
)
