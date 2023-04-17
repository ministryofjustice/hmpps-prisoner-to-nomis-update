package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance

@Service
class ActivitiesApiService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {

  suspend fun getActivitySchedule(activityScheduleId: Long): ActivitySchedule {
    return webClient.get()
      .uri("/schedules/$activityScheduleId")
      .retrieve()
      .awaitBody()
  }

  suspend fun getActivity(activityId: Long): Activity {
    return webClient.get()
      .uri("/activities/$activityId")
      .retrieve()
      .awaitBody()
  }

  suspend fun getAllocation(allocationId: Long): Allocation {
    return webClient.get()
      .uri("/allocations/id/$allocationId")
      .retrieve()
      .awaitBody()
  }

  suspend fun getAttendanceSync(attendanceId: Long): AttendanceSync {
    return webClient.get()
      .uri("/synchronisation/attendance/$attendanceId")
      .retrieve()
      .awaitBody()
  }

  suspend fun getScheduledInstance(scheduledInstanceId: Long): ScheduledInstance {
    return webClient.get()
      .uri("/scheduled-instances/$scheduledInstanceId")
      .retrieve()
      .awaitBody()
  }
}
