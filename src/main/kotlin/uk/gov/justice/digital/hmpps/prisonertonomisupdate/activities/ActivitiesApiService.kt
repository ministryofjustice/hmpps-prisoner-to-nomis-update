package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule

@Service
class ActivitiesApiService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {

  fun getActivitySchedule(activityScheduleId: Long): ActivitySchedule {
    return webClient.get()
      .uri("/schedules/$activityScheduleId")
      .retrieve()
      .bodyToMono(ActivitySchedule::class.java)
      .block()!!
  }

  fun getActivity(activityId: Long): Activity {
    return webClient.get()
      .uri("/activities/$activityId")
      .retrieve()
      .bodyToMono(Activity::class.java)
      .block()!!
  }
}
