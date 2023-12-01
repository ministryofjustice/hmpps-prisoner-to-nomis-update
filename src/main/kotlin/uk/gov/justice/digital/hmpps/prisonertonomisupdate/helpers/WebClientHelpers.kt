package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ErrorResponse

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullForNotFound(): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullForStatus(vararg status: HttpStatus): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException::class.java) {
      if (it.statusCode in status) Mono.empty() else Mono.error(it)
    }
    .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrUpsertAttendanceError(): T =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.BadRequest::class.java) {
      val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse
      UpsertAttendanceError.entries.find { error -> error.errorCode == errorResponse.errorCode }
        ?.let { err -> Mono.error(err.exception(errorResponse)) }
        ?: Mono.error(it)
    }
    .awaitSingle()

enum class UpsertAttendanceError(val errorCode: Int, val exception: (ErrorResponse) -> UpsertAttendanceException) {
  ATTENDANCE_PAID(1001, { AttendancePaidException(it) }),
  PRISONER_MOVED_ALLOCATION_ENDED(1002, { PrisonerMovedAllocationEndedException(it) }),
}

sealed class UpsertAttendanceException(errorResponse: ErrorResponse) : RuntimeException(errorResponse.userMessage)
class AttendancePaidException(errorResponse: ErrorResponse) : UpsertAttendanceException(errorResponse)
class PrisonerMovedAllocationEndedException(errorResponse: ErrorResponse) : UpsertAttendanceException(errorResponse)

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
