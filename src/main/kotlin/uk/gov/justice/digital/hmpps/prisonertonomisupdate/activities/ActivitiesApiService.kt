package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AllocationReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import java.time.LocalDate

@Service
class ActivitiesApiService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {

  suspend fun getActivitySchedule(activityScheduleId: Long): ActivitySchedule = webClient.get()
    .uri {
      it.path("/schedules/{activityScheduleId}")
        .queryParam("earliestSessionDate", LocalDate.now())
        .build(activityScheduleId)
    }
    .retrieve()
    .awaitBody()

  suspend fun getActivity(activityId: Long): Activity = webClient.get()
    .uri {
      it.path("/activities/{activityId}/filtered")
        .queryParam("earliestSessionDate", LocalDate.now())
        .build(activityId)
    }
    .retrieve()
    .awaitBody()

  suspend fun getAllocation(allocationId: Long): Allocation = webClient.get()
    .uri("/allocations/id/{allocationId}", allocationId)
    .retrieve()
    .awaitBody()

  suspend fun getAttendanceSync(attendanceId: Long): AttendanceSync = webClient.get()
    .uri("/synchronisation/attendance/{attendanceId}", attendanceId)
    .retrieve()
    .awaitBody()

  suspend fun getScheduledInstance(scheduledInstanceId: Long): ActivityScheduleInstance = webClient.get()
    .uri("/scheduled-instances/{scheduledInstanceId}", scheduledInstanceId)
    .retrieve()
    .awaitBody()

  suspend fun getAllocationReconciliation(prisonCode: String): AllocationReconciliationResponse = webClient.get()
    .uri("/synchronisation/reconciliation/allocations/{prisonCode}", prisonCode)
    .retrieve()
    .awaitBody()

  suspend fun getAttendanceReconciliation(prisonCode: String, date: LocalDate): AttendanceReconciliationResponse = webClient.get()
    .uri {
      it.path("/synchronisation/reconciliation/attendances/{prisonCode}")
        .queryParam("date", date)
        .build(prisonCode)
    }
    .retrieve()
    .awaitBody()
}
