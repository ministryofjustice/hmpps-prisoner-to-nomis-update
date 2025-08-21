package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentSearchRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentSearchResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.RolloutPrisonPlan
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference
import java.time.LocalDate

@Service
class AppointmentsApiService(
  @Qualifier("appointmentsApiWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.appointments.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.appointments.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  suspend fun getAppointmentInstance(id: Long): AppointmentInstance = webClient
    .get()
    .uri("/appointment-instances/{id}", id)
    .retrieve()
    .bodyToMono(AppointmentInstance::class.java)
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "casenotes-nomis-api", "url", "/appointment-instances/$id")))
    .awaitSingle()

  suspend fun getRolloutPrisons(): List<RolloutPrisonPlan> = webClient
    .get()
    .uri {
      it.path("/rollout")
        .queryParam("prisonsLive", true)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<List<RolloutPrisonPlan>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "casenotes-nomis-api", "url", "/rollout")))
    .awaitSingle()

  suspend fun searchAppointments(prisonId: String, startDate: LocalDate): List<AppointmentSearchResult> = webClient
    .post()
    .uri("/appointments/{prisonId}/search", prisonId)
    .bodyValue(AppointmentSearchRequest(startDate))
    .retrieve()
    .bodyToMono(typeReference<List<AppointmentSearchResult>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "casenotes-nomis-api", "url", "/appointments/$prisonId/search")))
    .awaitSingle()
}
