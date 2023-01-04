package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.LocalDateTime

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

// Copied from https://github.com/ministryofjustice/hmpps-activities-management-api
data class ActivitySchedule(

  @Schema(description = "The internally-generated ID for this activity schedule", example = "123456")
  val id: Long,

//  @Schema(description = "The planned instances associated with this activity schedule")
//  val instances: List<ScheduledInstance> = emptyList(),
//
//  @Schema(description = "The list of allocated prisoners who are allocated to this schedule, at this time and location")
//  val allocations: List<Allocation> = emptyList(),

  @Schema(description = "The description of this activity schedule", example = "Monday AM Houseblock 3")
  val description: String,

  @Schema(description = "Indicates the dates between which the schedule has been suspended")
  val suspensions: List<Suspension> = emptyList(),

  @Schema(description = "The NOMIS internal location for this schedule", example = "98877667")
  val internalLocation: InternalLocation? = null,

  @Schema(description = "The maximum number of prisoners allowed for a scheduled instance of this schedule", example = "10")
  val capacity: Int,

  @Schema(description = "The activity")
  val activity: ActivityLite,

//  @Schema(description = "The slots associated with this activity schedule")
//  val slots: List<ActivityScheduleSlot> = emptyList(),
)

data class Suspension(

  @Schema(description = "The date from which the activity schedule was suspended", example = "02/09/2022")
  val suspendedFrom: LocalDate,

  @Schema(
    description = "The date until which the activity schedule was suspended. If null, the schedule is suspended indefinately",
    example = "02/09/2022"
  )
  val suspendedUntil: LocalDate? = null,
)

data class InternalLocation(

  @Schema(description = "The NOMIS internal location id for this schedule", example = "98877667")
  val id: Int,

  @Schema(description = "The NOMIS internal location code for this schedule", example = "EDU-ROOM-1")
  val code: String,

  @Schema(description = "The NOMIS internal location description for this schedule", example = "Education - R1")
  val description: String
)

data class ActivityLite(

  @Schema(description = "The internally-generated ID for this activity", example = "123456")
  val id: Long,

  @Schema(description = "The prison code where this activity takes place", example = "PVI")
  val prisonCode: String,

  @Schema(description = "Flag to indicate if attendance is required for this activity, e.g. gym induction might not be mandatory attendance", example = "false")
  val attendanceRequired: Boolean,

  @Schema(description = "Flag to indicate if the location of the activity is in cell", example = "false")
  var inCell: Boolean,

  @Schema(description = "Flag to indicate if the activity is piece work", example = "false")
  var pieceWork: Boolean,

  @Schema(description = "Flag to indicate if the activity carried out outside of the prison", example = "false")
  var outsideWork: Boolean,

  @Schema(description = "A brief summary description of this activity for use in forms and lists", example = "Maths level 1")
  val summary: String,

  @Schema(description = "A detailed description for this activity", example = "A basic maths course suitable for introduction to the subject")
  val description: String?,

  @Schema(description = "The category for this activity, one of the high-level categories")
  val category: ActivityCategory,

  @Schema(description = "The most recent risk assessment level for this activity", example = "High")
  val riskLevel: String?,

  @Schema(description = "The minimum incentive/earned privilege level for this activity", example = "Basic")
  val minimumIncentiveLevel: String?,
)

data class ActivityCategory(
  @Schema(
    description = "The internally-generated identifier for this activity category",
    example = "1"
  )
  val id: Long,

  @Schema(description = "The activity category code", example = "LEISURE_SOCIAL")
  val code: String,

  @Schema(description = "The name of the activity category", example = "Leisure and social")
  val name: String,

  @Schema(description = "The description of the activity category", example = "Such as association, library time and social clubs, like music or art")
  val description: String?
)

data class Activity(

  @Schema(description = "The internally-generated ID for this activity", example = "123456")
  val id: Long,

  @Schema(description = "The prison code where this activity takes place", example = "PVI")
  val prisonCode: String,

  @Schema(description = "Flag to indicate if attendance is required for this activity, e.g. gym induction might not be mandatory attendance", example = "false")
  val attendanceRequired: Boolean,

  @Schema(description = "Flag to indicate if the location of the activity is in cell", example = "false")
  var inCell: Boolean,

  @Schema(description = "Flag to indicate if the activity is piece work", example = "false")
  var pieceWork: Boolean,

  @Schema(description = "Flag to indicate if the activity carried out outside of the prison", example = "false")
  var outsideWork: Boolean,

  @Schema(description = "A brief summary description of this activity for use in forms and lists", example = "Maths level 1")
  val summary: String,

  @Schema(description = "A detailed description for this activity", example = "A basic maths course suitable for introduction to the subject")
  val description: String?,

  @Schema(description = "The category for this activity, one of the high-level categories")
  val category: ActivityCategory,

//  @Schema(description = "The tier for this activity, as defined by the Future Prison Regime team", example = "Tier 1, Tier 2, Foundation")
//  val tier: ActivityTier?,

//  @Schema(description = "A list of eligibility rules which apply to this activity. These can be positive (include) and negative (exclude)", example = "[FEMALE_ONLY,AGED_18-25]")
//  val eligibilityRules: List<ActivityEligibility> = emptyList(),

  @Schema(description = "A list of schedules for this activity. These contain the time slots / recurrence settings for instances of this activity.")
  val schedules: List<ActivitySchedule> = emptyList(),

//  @Schema(description = "A list of prisoners who are waiting for allocation to this activity. This list is held against the activity, though allocation is against particular schedules of the activity")
//  val waitingList: List<PrisonerWaiting> = emptyList(),

  @Schema(description = "The list of pay rates by incentive level and pay band that can apply to this activity")
  val pay: List<ActivityPay> = emptyList(),

  @Schema(description = "The date on which this activity will start. From this date, any schedules will be created as real, planned instances", example = "21/09/2022")
  val startDate: LocalDate,

  @Schema(description = "The date on which this activity ends. From this date, there will be no more planned instances of the activity. If null, the activity has no end date and will be scheduled indefinitely.", example = "21/12/2022")
  val endDate: LocalDate? = null,

  @Schema(description = "The most recent risk assessment level for this activity", example = "High")
  val riskLevel: String?,

  @Schema(description = "The minimum incentive/earned privilege level for this activity", example = "Basic")
  val minimumIncentiveLevel: String?,

  @Schema(description = "The date and time when this activity was created", example = "01/09/2022 9:00")
  val createdTime: LocalDateTime,

  @Schema(description = "The person who created this activity", example = "Adam Smith")
  val createdBy: String
)

data class ActivityPay(

  @Schema(description = "The internally-generated ID for this activity pay", example = "123456")
  val id: Long,

  @Schema(description = "The incentive/earned privilege level (nullable)", example = "Basic")
  val incentiveLevel: String? = null,

  @Schema(description = "The pay band (nullable)", example = "A")
  val payBand: String? = null,

  @Schema(description = "The earning rate for one half day session for someone of this incentive level and pay band (in pence)", example = "150")
  val rate: Int? = null,

  @Schema(description = "Where payment is related to produced amounts of a product, this indicates the payment rate (in pence) per pieceRateItems produced", example = "150")
  val pieceRate: Int? = null,

  @Schema(description = "Where payment is related to the number of items produced in a batch of a product, this is the batch size that attract 1 x pieceRate", example = "10")
  val pieceRateItems: Int? = null,
)
