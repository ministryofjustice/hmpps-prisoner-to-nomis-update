package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ErrorResponse

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNotFound(): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrAttendancePaidException(): T =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.BadRequest::class.java) {
      val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java)
      if (errorResponse.errorCode == BadRequestError.ATTENDANCE_PAID.errorCode) {
        Mono.error(AttendancePaidException(errorResponse))
      } else {
        Mono.error(it)
      }
    }
    .awaitSingle()

enum class BadRequestError(val errorCode: Int) {
  ATTENDANCE_PAID(1001),
}

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

class AttendancePaidException(val error: ErrorResponse) : RuntimeException("message")
