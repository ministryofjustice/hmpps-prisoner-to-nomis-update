package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrUpsertAttendanceError
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AllocationReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AttendanceReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourseScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCourseScheduleResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceResponse
import java.time.LocalDate

@Service
class ActivitiesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun createActivity(request: CreateActivityRequest): CreateActivityResponse =
    webClient.post()
      .uri("/activities")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateActivity(courseActivityId: Long, request: UpdateActivityRequest): CreateActivityResponse =
    webClient.put()
      .uri("/activities/{courseActivityId}", courseActivityId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun deleteActivity(courseActivityId: Long) {
    webClient.delete()
      .uri("/activities/{courseActivityId}", courseActivityId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updateScheduledInstance(
    courseActivityId: Long,
    request: CourseScheduleRequest,
  ): UpdateCourseScheduleResponse =
    webClient.put()
      .uri("/activities/{courseActivityId}/schedule", courseActivityId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun upsertAllocation(
    courseActivityId: Long,
    request: UpsertAllocationRequest,
  ): UpsertAllocationResponse =
    webClient.put()
      .uri("/activities/{courseActivityId}/allocation", courseActivityId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun upsertAttendance(
    courseScheduleId: Long,
    bookingId: Long,
    request: UpsertAttendanceRequest,
  ): UpsertAttendanceResponse =
    webClient.put()
      .uri("/schedules/{courseScheduleId}/booking/{bookingId}/attendance", courseScheduleId, bookingId)
      .bodyValue(request)
      .retrieve()
      .awaitBodyOrUpsertAttendanceError()

  suspend fun getAllocationReconciliation(prisonId: String): AllocationReconciliationResponse =
    webClient.get()
      .uri("/allocations/reconciliation/{prisonId}", prisonId)
      .retrieve()
      .awaitBody()

  suspend fun getAttendanceReconciliation(prisonId: String, date: LocalDate): AttendanceReconciliationResponse =
    webClient.get()
      .uri {
        it.path("/attendances/reconciliation/{prisonId}")
          .queryParam("date", date)
          .build(prisonId)
      }
      .retrieve()
      .awaitBody()

  suspend fun getServicePrisons(serviceCode: String): List<PrisonDetails> =
    webClient.get()
      .uri("/service-prisons/{serviceCode}", serviceCode)
      .retrieve()
      .awaitBody()

  suspend fun getPrisonerDetails(bookingIds: List<Long>): List<PrisonerDetails> =
    webClient.post()
      .uri("/prisoners/bookings")
      .bodyValue(bookingIds)
      .retrieve()
      .awaitBody()

  suspend fun getMaxCourseScheduleId(): Long =
    webClient.get()
      .uri("/schedules/max-id")
      .retrieve()
      .awaitBody()
}
