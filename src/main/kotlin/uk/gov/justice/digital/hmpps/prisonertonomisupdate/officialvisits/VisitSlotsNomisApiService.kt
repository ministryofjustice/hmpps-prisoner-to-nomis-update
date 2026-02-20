package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ActivePrisonWithTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitTimeSlotForPrisonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class VisitSlotsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = VisitsConfigurationResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "VisitSlotsNomisApiService"),
  )

  suspend fun getTimeSlotsForPrison(prisonId: String, activeOnly: Boolean = false): VisitTimeSlotForPrisonResponse = api.getPrisonVisitTimeSlots(prisonId = prisonId, activeOnly = activeOnly)
    .retryWhen(retrySpec).awaitSingle()

  suspend fun getActivePrisonsWithTimeSlots(): ActivePrisonWithTimeSlotResponse = api.getActivePrisonsWithTimeSlots()
    .retryWhen(retrySpec).awaitSingle()

  suspend fun createTimeSlot(prisonId: String, dayOfWeek: VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot, request: CreateVisitTimeSlotRequest) = api.createVisitTimeSlot(
    prisonId = prisonId,
    dayOfWeek = dayOfWeek,
    createVisitTimeSlotRequest = request,
  ).awaitSingle()

  suspend fun deleteTimeSlot(prisonId: String, dayOfWeek: VisitsConfigurationResourceApi.DayOfWeekDeleteVisitTimeSlot, timeSlotSequence: Int) = api.deleteVisitTimeSlot(
    prisonId = prisonId,
    dayOfWeek = dayOfWeek,
    timeSlotSequence = timeSlotSequence,
  ).awaitSingle()

  suspend fun createVisitSlot(prisonId: String, dayOfWeek: VisitsConfigurationResourceApi.DayOfWeekCreateVisitSlot, timeSlotSequence: Int, request: CreateVisitSlotRequest) = api.createVisitSlot(
    prisonId = prisonId,
    dayOfWeek = dayOfWeek,
    timeSlotSequence = timeSlotSequence,
    createVisitSlotRequest = request,
  ).awaitSingle()

  suspend fun deleteVisitSlot(visitSlotId: Long) = api.deleteVisitSlot(visitSlotId = visitSlotId).awaitSingle()
}
