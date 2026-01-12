package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate

internal const val ACTIVITY_SCHEDULE_ID: Long = 100
internal const val ACTIVITY_ID: Long = 200
internal const val NOMIS_CRS_ACTY_ID: Long = 300
internal const val ALLOCATION_ID: Long = 400
internal const val NOMIS_BOOKING_ID: Long = 500
internal const val OFFENDER_PROGRAM_REFERENCE_ID: Long = 550
internal const val SCHEDULE_INSTANCE_ID: Long = 600
internal const val ATTENDANCE_ID: Long = 700
internal const val NOMIS_EVENT_ID: Long = 800
internal const val NOMIS_CRS_SCH_ID: Long = 900
internal const val OFFENDER_NO = "A1234AA"

class ActivityToNomisIntTest : SqsIntegrationTestBase() {

  private val today = LocalDate.now()
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Nested
  inner class CreateActivitySchedule {

    @Test
    fun `will consume a create activity schedule message`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubCreateActivity()
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      nomisApi.stubActivityCreate(buildNomisActivityResponse())

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("code", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("prisonId", equalTo("PVI")))
          .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$ACTIVITIES_NOMIS_LOCATION_ID")))
          .withRequestBody(matchingJsonPath("capacity", equalTo("10")))
          .withRequestBody(matchingJsonPath("payRates[0].incentiveLevel", equalTo("BAS")))
          .withRequestBody(matchingJsonPath("payRates[0].payBand", equalTo("1")))
          .withRequestBody(matchingJsonPath("payRates[0].rate", equalTo("1.5")))
          .withRequestBody(matchingJsonPath("description", equalTo("Maths level 1")))
          .withRequestBody(matchingJsonPath("minimumIncentiveLevelCode", absent()))
          .withRequestBody(matchingJsonPath("programCode", equalTo("LEISURE_SOCIAL")))
          .withRequestBody(matchingJsonPath("payPerSession", equalTo("F")))
          .withRequestBody(matchingJsonPath("outsideWork", equalTo("true")))
          .withRequestBody(matchingJsonPath("schedules[0].id", absent()))
          .withRequestBody(matchingJsonPath("schedules[0].date", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("schedules[0].startTime", equalTo("09:00")))
          .withRequestBody(matchingJsonPath("schedules[0].endTime", equalTo("10:00")))
          .withRequestBody(matchingJsonPath("schedules[1].id", absent()))
          .withRequestBody(matchingJsonPath("schedules[1].date", equalTo("2023-01-14")))
          .withRequestBody(matchingJsonPath("schedules[1].startTime", equalTo("14:00")))
          .withRequestBody(matchingJsonPath("schedules[1].endTime", equalTo("16:30")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].startTime", equalTo("07:45")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].endTime", equalTo("09:25")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].sunday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].tuesday", equalTo("false")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].wednesday", equalTo("false")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].thursday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].startTime", equalTo("13:45")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].endTime", equalTo("14:25")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].sunday", equalTo("false")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].tuesday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].wednesday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].thursday", equalTo("false")))
          .withRequestBody(matchingJsonPath("excludeBankHolidays", equalTo("false"))),
      )
      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
          .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("activityId", equalTo("$ACTIVITY_ID")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_CREATED")))
          .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].scheduledInstanceId", equalTo("$SCHEDULE_INSTANCE_ID")))
          .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].nomisCourseScheduleId", equalTo("$NOMIS_CRS_SCH_ID")))
          .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].mappingType", equalTo("ACTIVITY_CREATED")))
          .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].scheduledInstanceId", equalTo("${SCHEDULE_INSTANCE_ID + 1}")))
          .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].nomisCourseScheduleId", equalTo("${NOMIS_CRS_SCH_ID + 1}")))
          .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].mappingType", equalTo("ACTIVITY_CREATED"))),
      )
      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/locations/dps/$ACTIVITIES_DPS_LOCATION_ID")),
      )
    }

    @Test
    fun `will retry after a mapping failure`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      nomisApi.stubActivityCreate(buildNomisActivityResponse())
      mappingServer.stubCreateActivityWithErrorFollowedBySuccess()

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      await untilAsserted { verify(telemetryClient).trackEvent(eq("activity-create-mapping-retry"), any(), isNull()) }
      await untilAsserted {
        mappingServer.verify(
          exactly(2),
          postRequestedFor(urlEqualTo("/mapping/activities"))
            .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
            .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
            .withRequestBody(matchingJsonPath("activityId", equalTo("$ACTIVITY_ID")))
            .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_CREATED")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].scheduledInstanceId", equalTo("$SCHEDULE_INSTANCE_ID")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].nomisCourseScheduleId", equalTo("$NOMIS_CRS_SCH_ID")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].mappingType", equalTo("ACTIVITY_CREATED")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].scheduledInstanceId", equalTo("${SCHEDULE_INSTANCE_ID + 1}")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].nomisCourseScheduleId", equalTo("${NOMIS_CRS_SCH_ID + 1}")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].mappingType", equalTo("ACTIVITY_CREATED"))),
        )
      }

      // no messages sent to DLQ
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 0 }
    }

    @Test
    fun `will log when duplicate is detected`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      nomisApi.stubActivityCreate("""{ "courseActivityId": $NOMIS_CRS_ACTY_ID, "courseSchedules": [] }""")
      mappingServer.stubCreateActivityWithDuplicateError(
        activityScheduleId = ACTIVITY_SCHEDULE_ID,
        nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
        duplicateNomisCourseActivityId = 301,
      )

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("activity-mapping-create-failed"),
          any(),
          isNull(),
        )
      }

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      await untilAsserted { mappingServer.verify(exactly(1), postRequestedFor(urlEqualTo("/mapping/activities"))) }

      // no messages sent to DLQ
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 0 }

      verify(telemetryClient).trackEvent(
        eq("to-nomis-synch-activity-duplicate"),
        check {
          assertThat(it["existingActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["existingNomisCourseActivityId"]).isEqualTo("$NOMIS_CRS_ACTY_ID")
          assertThat(it["duplicateActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["duplicateNomisCourseActivityId"]).isEqualTo("301")
          assertThat(it["prisonId"]).isEqualTo("PVI")
        },
        isNull(),
      )
    }

    @Test
    fun `constant mapping failure will result in DLQ message which can be retried`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      nomisApi.stubActivityCreate("""{ "courseActivityId": $NOMIS_CRS_ACTY_ID, "courseSchedules": [] }""")
      mappingServer.stubCreateActivityWithError()

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }

      // the mapping call fails resulting in a retry message being queued
      // the retry message is processed and fails resulting in a message on the DLQ after 1 attempt
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 1 }

      // Next time the retry will succeed
      mappingServer.stubCreateActivity()

      webTestClient.put()
        .uri("/queue-admin/retry-all-dlqs")
        .exchange()
        .expectStatus()
        .isOk

      await untilCallTo { mappingServer.postCountFor("/mapping/activities") } matches { it == 3 } // 1 initial call, 1 retry and 1 final successful call
      await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches {
        log.trace("Messages on queue: {}", it)
        it == 0
      }
    }

    @Test
    fun `location mapping failure will result in DLQ message`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      // cannot find DPS mapping
      mappingServer.stubGetMappingGivenDpsLocationIdWithError(ACTIVITIES_DPS_LOCATION_ID, 404)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today") } matches { it == 1 }

      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 1 }
    }

    @Test
    fun `will retry and publish telemetry after a Nomis update failure`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      mappingServer.stubCreateActivity()
      nomisApi.stubActivityCreateWithError(status = 500)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }

      // message sent to DLQ
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 1 }

      // telemetry is published for the failure
      verify(telemetryClient).trackEvent(
        eq("activity-create-failed"),
        check {
          assertThat(it["dpsActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["dpsActivityId"]).isEqualTo("$ACTIVITY_ID")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class UpdateActivitySchedule {
    @Test
    fun `should update an activity`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      nomisApi.stubActivityUpdate(NOMIS_CRS_ACTY_ID, buildNomisActivityResponse())
      mappingServer.stubUpdateActivity()

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/schedules/$ACTIVITY_SCHEDULE_ID?earliestSessionDate=$today"))) }
      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today"))) }
      await untilAsserted { mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted {
        nomisApi.verify(
          putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID"))
            .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-20")))
            .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-23")))
            .withRequestBody(matchingJsonPath("capacity", equalTo("10")))
            .withRequestBody(matchingJsonPath("description", equalTo("Monday AM Houseblock 3")))
            .withRequestBody(matchingJsonPath("minimumIncentiveLevelCode", absent()))
            .withRequestBody(matchingJsonPath("payPerSession", equalTo("F")))
            .withRequestBody(matchingJsonPath("outsideWork", equalTo("true")))
            .withRequestBody(matchingJsonPath("excludeBankHolidays", equalTo("false")))
            .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$ACTIVITIES_NOMIS_LOCATION_ID")))
            .withRequestBody(matchingJsonPath("payRates[0].incentiveLevel", equalTo("BAS")))
            .withRequestBody(matchingJsonPath("payRates[0].payBand", equalTo("1")))
            .withRequestBody(matchingJsonPath("payRates[0].rate", equalTo("1.5")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].startTime", equalTo("07:45")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].endTime", equalTo("09:25")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].tuesday", equalTo("false")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].thursday", equalTo("true")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].startTime", equalTo("13:45")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].endTime", equalTo("14:25")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].tuesday", equalTo("true")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].thursday", equalTo("false")))
            .withRequestBody(matchingJsonPath("schedules[0].id", equalTo("$NOMIS_CRS_SCH_ID")))
            .withRequestBody(matchingJsonPath("schedules[0].date", equalTo("2023-01-13")))
            .withRequestBody(matchingJsonPath("schedules[0].startTime", equalTo("09:00")))
            .withRequestBody(matchingJsonPath("schedules[0].endTime", equalTo("10:00")))
            .withRequestBody(matchingJsonPath("schedules[0].cancelled", equalTo("false")))
            .withRequestBody(matchingJsonPath("schedules[1].id", absent()))
            .withRequestBody(matchingJsonPath("schedules[1].date", equalTo("2023-01-14")))
            .withRequestBody(matchingJsonPath("schedules[1].startTime", equalTo("14:00")))
            .withRequestBody(matchingJsonPath("schedules[1].endTime", equalTo("16:30")))
            .withRequestBody(matchingJsonPath("schedules[1].cancelled", equalTo("true")))
            .withRequestBody(matchingJsonPath("programCode", equalTo("LEISURE_SOCIAL"))),
        )
        mappingServer.verify(
          putRequestedFor(urlEqualTo("/mapping/activities"))
            .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
            .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
            .withRequestBody(matchingJsonPath("activityId", equalTo("$ACTIVITY_ID")))
            .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_UPDATED")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].scheduledInstanceId", equalTo("$SCHEDULE_INSTANCE_ID")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].nomisCourseScheduleId", equalTo("$NOMIS_CRS_SCH_ID")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].mappingType", equalTo("ACTIVITY_UPDATED")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].scheduledInstanceId", equalTo("${SCHEDULE_INSTANCE_ID + 1}")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].nomisCourseScheduleId", equalTo("${NOMIS_CRS_SCH_ID + 1}")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].mappingType", equalTo("ACTIVITY_UPDATED"))),
        )
      }
      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/locations/dps/$ACTIVITIES_DPS_LOCATION_ID")),
      )
      await untilAsserted { mappingServer.verify(putRequestedFor(urlEqualTo("/mapping/activities"))) }
      assertThat(awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }

    @Test
    fun `should put message on DLQ if any external API fails`() {
      activitiesApi.stubGetScheduleWithError(ACTIVITY_SCHEDULE_ID)

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted {
        assertThat(
          awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get(),
        ).isEqualTo(1)
      }
      nomisApi.verify(exactly(0), putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID")))
    }

    @Test
    fun `should handle a partial list of activity schedules returned from Activities`() {
      // The first scheduled instance isn't returned from the Activities API
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponseWithMissingInstance())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      mappingServer.stubGetMappingGivenDpsLocationId(ACTIVITIES_DPS_LOCATION_ID, activitiesLocationMappingResponse)
      nomisApi.stubActivityUpdate(NOMIS_CRS_ACTY_ID, buildNomisActivityResponse())
      mappingServer.stubUpdateActivity()

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/schedules/$ACTIVITY_SCHEDULE_ID?earliestSessionDate=$today"))) }
      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/activities/$ACTIVITY_ID/filtered?earliestSessionDate=$today"))) }
      await untilAsserted { mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      // Both mappings are saved back to the mapping service - including the one no returned from the Activities API
      await untilAsserted {
        nomisApi.verify(putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID")))
        mappingServer.verify(
          putRequestedFor(urlEqualTo("/mapping/activities"))
            .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
            .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
            .withRequestBody(matchingJsonPath("activityId", equalTo("$ACTIVITY_ID")))
            .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_UPDATED")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].scheduledInstanceId", equalTo("$SCHEDULE_INSTANCE_ID")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].nomisCourseScheduleId", equalTo("$NOMIS_CRS_SCH_ID")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[0].mappingType", equalTo("ACTIVITY_UPDATED")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].scheduledInstanceId", equalTo("${SCHEDULE_INSTANCE_ID + 1}")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].nomisCourseScheduleId", equalTo("${NOMIS_CRS_SCH_ID + 1}")))
            .withRequestBody(matchingJsonPath("scheduledInstanceMappings[1].mappingType", equalTo("ACTIVITY_UPDATED"))),
        )
      }
      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/locations/dps/$ACTIVITIES_DPS_LOCATION_ID")),
      )
      assertThat(awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }

    private fun amendActivityEvent(): PublishRequest? = PublishRequest.builder().topicArn(topicArn)
      .message(activityMessagePayload("activities.activity-schedule.amended", ACTIVITY_SCHEDULE_ID))
      .messageAttributes(
        mapOf(
          "eventType" to MessageAttributeValue.builder().dataType("String")
            .stringValue("activities.activity-schedule.amended").build(),
        ),
      ).build()
  }
}

fun buildGetActivityResponse(
  id: Long = ACTIVITY_ID,
  payRates: String = """
    "pay": [
      {
        "id": 3579,
        "incentiveLevel": "Basic",
        "incentiveNomisCode": "BAS",
        "prisonPayBand": {
           "id": 987,
           "displaySequence": 1,
           "alias": "Low",
           "description": "Pay band 1",
           "nomisPayBand": 1,
           "prisonCode": "PVI"
        },
        "rate": 150,
        "pieceRate": 250,
        "pieceRateItems": 10
      }
    ],
  """.trimIndent(),
): String =
  """
    {
  "id": $id,
  "prisonCode": "PVI",
  "attendanceRequired": false,
  "inCell": false,
  "pieceWork": false,
  "outsideWork": true,
  "payPerSession": "F",
  "summary": "Maths level 1",
  "description": "A basic maths course suitable for introduction to the subject",
  "onWing": false,
  "offWing": true,
  "paid": false,
  "category": {
    "id": 1,
    "code": "LEISURE_SOCIAL",
    "name": "Leisure and social",
    "description": "Such as association, library time and social clubs, like music or art"
  },
  "eligibilityRules": [],
  "schedules": [],
  "waitingList": [],
  $payRates
  "payChange": [],
  "startDate": "2023-01-12",
  "endDate": "2023-01-13",
  "riskLevel": "High",
  "minimumIncentiveLevel": "Basic",
  "minimumIncentiveNomisCode": "BAS",
  "createdTime": "2023-01-12T17:26:18.332Z",
  "createdBy": "Adam Smith",
  "minimumEducationLevel": [
    {
      "id": 123456,
      "educationLevelCode": "Basic",
      "educationLevelDescription": "Basic",
      "studyAreaCode": "ENGLA",
      "studyAreaDescription":  "English language"
    }
  ],
  "createdTime": "2023-06-01T09:17:30.425Z",
  "activityState": "LIVE",
  "capacity": 10,
  "allocated": 5
}
  """.trimIndent()

fun buildGetScheduleResponse(id: Long = ACTIVITY_SCHEDULE_ID): String =
  """
{
  "id": $id,
  "instances": [
    {
      "id": $SCHEDULE_INSTANCE_ID,
      "date": "2023-01-13",
      "startTime": "09:00",
      "endTime": "10:00",
      "cancelled": false,
      "cancelledTime": "2023-01-13T09:38:26.092Z",
      "cancelledBy": "Adam Smith",
      "attendances": [],
      "timeSlot": "AM",
      "advanceAttendances": []
    },
    {
      "id": ${SCHEDULE_INSTANCE_ID + 1},
      "date": "2023-01-14",
      "startTime": "14:00",
      "endTime": "16:30",
      "cancelled": true,
      "cancelledTime": "2023-01-13T09:38:26.092Z",
      "cancelledBy": "Adam Smith",
      "attendances": [],
      "timeSlot": "PM",
      "advanceAttendances": []
    }
  ],
  "allocations": [],
  "description": "Monday AM Houseblock 3",
  "suspensions": [
    {
      "suspendedFrom": "2023-01-13",
      "suspendedUntil": "2023-01-13"
    }
  ],
  "usePrisonRegimeTime": true,
  "internalLocation": {
    "id": $ACTIVITIES_NOMIS_LOCATION_ID,
    "code": "EDU-ROOM-1",
    "description": "Education - R1",
    "dpsLocationId": "$ACTIVITIES_DPS_LOCATION_ID"
  },
  "capacity": 10,
  "activity": {
    "id": $ACTIVITY_ID,
    "prisonCode": "PVI",
    "attendanceRequired": false,
    "inCell": false,
    "pieceWork": false,
    "outsideWork": false,
    "payPerSession": "F",
    "summary": "Maths level 1",
    "description": "A basic maths course suitable for introduction to the subject",
    "onWing": false,
    "offWing": true,
    "paid": false,
    "category": {
      "id": 1,
      "code": "LEISURE_SOCIAL",
      "name": "Leisure and social",
      "description": "Such as association, library time and social clubs, like music or art"
    },
    "riskLevel": "High",
    "minimumIncentiveLevel": "Basic",
    "minimumIncentiveNomisCode": "BAS",
    "minimumEducationLevel": [
      {
        "id": 123456,
        "educationLevelCode": "Basic",
        "educationLevelDescription": "Basic",
        "studyAreaCode": "ENGLA",
        "studyAreaDescription":  "English language"
      }
    ],
    "createdTime": "2023-06-01T09:17:30.425Z",
    "activityState": "LIVE",
    "capacity": 10,
    "allocated": 5
  },
  "scheduleWeeks": 1,
  "slots": [{
    "id"        : 555666001,
    "startTime" : "07:45",
    "endTime"   : "09:25",
    "weekNumber": 2,
    "daysOfWeek": ["Sun","Thu"],
    "mondayFlag": false,
    "tuesdayFlag": false,
    "wednesdayFlag": false,
    "thursdayFlag": true,
    "fridayFlag": false,
    "saturdayFlag": false,
    "sundayFlag": true,
    "timeSlot": "AM"
  },
  {
    "id"        : 555666002,
    "startTime" : "13:45",
    "endTime"   : "14:25",
    "weekNumber": 2,
    "daysOfWeek": ["Tue","Wed"],
    "mondayFlag": false,
    "tuesdayFlag": true,
    "wednesdayFlag": true,
    "thursdayFlag": false,
    "fridayFlag": false,
    "saturdayFlag": false,
    "sundayFlag": false,
    "timeSlot": "PM"
  }],
  "startDate" : "2023-01-20",
  "endDate" : "2023-01-23",
  "runsOnBankHoliday": true
}
  """.trimIndent()

fun buildGetScheduleResponseWithMissingInstance(id: Long = ACTIVITY_SCHEDULE_ID): String =
  """
{
  "id": $id,
  "instances": [
    {
      "id": ${SCHEDULE_INSTANCE_ID + 1},
      "date": "2023-01-14",
      "startTime": "14:00",
      "endTime": "16:30",
      "cancelled": true,
      "cancelledTime": "2023-01-13T09:38:26.092Z",
      "cancelledBy": "Adam Smith",
      "attendances": [],
      "timeSlot": "PM",
      "advanceAttendances": []
    }
  ],
  "allocations": [],
  "description": "Monday AM Houseblock 3",
  "suspensions": [
    {
      "suspendedFrom": "2023-01-13",
      "suspendedUntil": "2023-01-13"
    }
  ],
  "usePrisonRegimeTime": true,
  "internalLocation": {
    "id": $ACTIVITIES_NOMIS_LOCATION_ID,
    "code": "EDU-ROOM-1",
    "description": "Education - R1",
    "dpsLocationId": "$ACTIVITIES_DPS_LOCATION_ID"
  },
  "capacity": 10,
  "activity": {
    "id": $ACTIVITY_ID,
    "prisonCode": "PVI",
    "attendanceRequired": false,
    "inCell": false,
    "pieceWork": false,
    "outsideWork": false,
    "payPerSession": "F",
    "summary": "Maths level 1",
    "description": "A basic maths course suitable for introduction to the subject",
    "onWing": false,
    "offWing": true,
    "paid": false,
    "category": {
      "id": 1,
      "code": "LEISURE_SOCIAL",
      "name": "Leisure and social",
      "description": "Such as association, library time and social clubs, like music or art"
    },
    "riskLevel": "High",
    "minimumIncentiveLevel": "Basic",
    "minimumIncentiveNomisCode": "BAS",
    "minimumEducationLevel": [
      {
        "id": 123456,
        "educationLevelCode": "Basic",
        "educationLevelDescription": "Basic",
        "studyAreaCode": "ENGLA",
        "studyAreaDescription":  "English language"
      }
    ],
    "createdTime": "2023-06-01T09:17:30.425Z",
    "activityState": "LIVE",
    "capacity": 10,
    "allocated": 5
  },
  "scheduleWeeks": 1,
  "slots": [{
    "id"        : 555666001,
    "startTime" : "07:45",
    "endTime"   : "09:25",
    "weekNumber": 3,
    "daysOfWeek": ["Sun","Thu"],
    "mondayFlag": false,
    "tuesdayFlag": false,
    "wednesdayFlag": false,
    "thursdayFlag": true,
    "fridayFlag": false,
    "saturdayFlag": false,
    "sundayFlag": true,
    "timeSlot": "AM"
  },
  {
    "id"        : 555666002,
    "startTime" : "13:45",
    "endTime"   : "14:25",
    "weekNumber": 3,
    "daysOfWeek": ["Tue","Wed"],
    "mondayFlag": false,
    "tuesdayFlag": true,
    "wednesdayFlag": true,
    "thursdayFlag": false,
    "fridayFlag": false,
    "saturdayFlag": false,
    "sundayFlag": false,
    "timeSlot": "PM"
  }],
  "startDate" : "2023-01-20",
  "endDate" : "2023-01-23",
  "runsOnBankHoliday": true
}
  """.trimIndent()

fun buildGetMappingResponse(
  nomisActivityId: Long = NOMIS_CRS_ACTY_ID,
  activityScheduleId: Long = ACTIVITY_SCHEDULE_ID,
  activityId: Long = ACTIVITY_ID,
) = """{
          "nomisCourseActivityId": $nomisActivityId,
          "activityScheduleId": $activityScheduleId,
          "activityId": $activityId,
          "mappingType": "TYPE",
          "scheduledInstanceMappings": [{
            "scheduledInstanceId": "$SCHEDULE_INSTANCE_ID",
            "nomisCourseScheduleId": "$NOMIS_CRS_SCH_ID",
            "mappingType": "ACTIVITY_CREATED"
          }]
        }
""".trimIndent()

fun buildNomisActivityResponse() = """{
               "courseActivityId": $NOMIS_CRS_ACTY_ID, 
               "courseSchedules": [
                 {
                   "courseScheduleId": $NOMIS_CRS_SCH_ID,
                   "date": "2023-01-13",
                   "startTime": "09:00:00",
                   "endTime": "10:00:00"
                 },
                 {
                   "courseScheduleId": ${NOMIS_CRS_SCH_ID + 1},
                   "date": "2023-01-14",
                   "startTime": "14:00:00",
                   "endTime": "16:30:00"
                 }
               ] 
             }
""".trimIndent()
