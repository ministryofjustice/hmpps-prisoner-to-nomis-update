package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementResponse
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

  suspend fun createScheduledTemporaryAbsence(offenderNo: String, request: CreateScheduledTemporaryAbsenceRequest): CreateScheduledTemporaryAbsenceReturnResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/scheduled-temporary-absence", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}
