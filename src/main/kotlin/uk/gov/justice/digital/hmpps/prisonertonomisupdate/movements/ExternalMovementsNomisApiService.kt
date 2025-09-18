package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class ExternalMovementsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ExternalMovementsNomisApiService"),
  )

  suspend fun createTemporaryAbsenceApplication(offenderNo: String, request: CreateTemporaryAbsenceApplicationRequest): CreateTemporaryAbsenceApplicationResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/application", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createTemporaryAbsenceOutsideMovement(offenderNo: String, request: CreateTemporaryAbsenceOutsideMovementRequest): CreateTemporaryAbsenceOutsideMovementResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/outside-movement", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createScheduledTemporaryAbsence(offenderNo: String, request: CreateScheduledTemporaryAbsenceRequest): CreateScheduledTemporaryAbsenceResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/scheduled-temporary-absence", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createScheduledTemporaryAbsenceReturn(offenderNo: String, request: CreateScheduledTemporaryAbsenceReturnRequest): CreateScheduledTemporaryAbsenceReturnResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/scheduled-temporary-absence-return", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createTemporaryAbsence(offenderNo: String, request: CreateTemporaryAbsenceRequest): CreateTemporaryAbsenceResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/temporary-absence", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createTemporaryAbsenceReturn(offenderNo: String, request: CreateTemporaryAbsenceReturnRequest): CreateTemporaryAbsenceReturnResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/temporary-absence-return", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}
