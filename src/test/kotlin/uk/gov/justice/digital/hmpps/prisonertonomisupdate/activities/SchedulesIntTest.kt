package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.scheduledInstanceMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class SchedulesIntTest(@Autowired private val schedulesService: SchedulesService) : SqsIntegrationTestBase() {

  @Nested
  inner class UpdateScheduledInstance {
    @Test
    fun `should update scheduled instance`() {
      ActivitiesApiExtension.activitiesApi.stubGetScheduledInstance(SCHEDULE_INSTANCE_ID, buildGetScheduledInstanceResponse())
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      nomisApi.stubScheduledInstanceUpdate(NOMIS_CRS_ACTY_ID, """{ "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

      awsSnsClient.publish(amendScheduledInstanceEvent())

      await untilAsserted { ActivitiesApiExtension.activitiesApi.verify(getRequestedFor(urlEqualTo("/scheduled-instances/$SCHEDULE_INSTANCE_ID"))) }
      await untilAsserted { MappingExtension.mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted {
        NomisApiExtension.nomisApi.verify(
          putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/schedule"))
            .withRequestBody(matchingJsonPath("id", equalTo("$NOMIS_CRS_SCH_ID")))
            .withRequestBody(matchingJsonPath("date", equalTo("2023-02-23")))
            .withRequestBody(matchingJsonPath("startTime", equalTo("08:00")))
            .withRequestBody(matchingJsonPath("endTime", equalTo("11:00"))),
        )
      }
      assertThat(awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }

    @Test
    fun `should put messages on DLQ if external API call fails`() {
      ActivitiesApiExtension.activitiesApi.stubGetScheduledInstance(SCHEDULE_INSTANCE_ID, buildGetScheduledInstanceResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubScheduledInstanceUpdateWithError(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(amendScheduledInstanceEvent())

      await untilAsserted {
        assertThat(
          awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get(),
        ).isEqualTo(1)
      }
    }

    private fun amendScheduledInstanceEvent(): PublishRequest? = PublishRequest.builder().topicArn(topicArn)
      .message(scheduledInstanceMessagePayload("activities.scheduled-instance.amended", SCHEDULE_INSTANCE_ID))
      .messageAttributes(
        mapOf(
          "eventType" to MessageAttributeValue.builder().dataType("String")
            .stringValue("activities.scheduled-instance.amended").build(),
        ),
      ).build()
  }

  @Nested
  inner class DeleteUnknownMappings {

    @Test
    fun `should delete unknown mappings`() = runTest {
      nomisApi.stubGetMaxCourseScheduleId(100)
      mappingServer.stubDeleteMappingsGreaterThan(100)

      schedulesService.deleteUnknownMappings()

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/schedules/max-id")),
      )
      mappingServer.verify(
        deleteRequestedFor(urlEqualTo("/mapping/schedules/max-nomis-schedule-id/100")),
      )
    }
  }
}

private fun buildGetScheduledInstanceResponse() =
  """
    {
      "id": $SCHEDULE_INSTANCE_ID,
      "date": "2023-02-23",
      "startTime": "08:00",
      "endTime": "11:00",
      "cancelled": true,
      "attendances": [],
      "cancelledTime": null,
      "cancelledBy": null,
      "timeSlot": "AM",
      "activitySchedule": {
        "id": $ACTIVITY_SCHEDULE_ID,
        "description": "activity",
        "capacity": 5,
        "slots": [],
        "scheduleWeeks": 3,
        "usePrisonRegimeTime": false,
        "startDate": "2023-02-01",
        "activity": {
          "id": $ACTIVITY_ID,
          "prisonCode": "LEI",
          "attendanceRequired": true,
          "inCell": false,
          "pieceWork": false,
          "outsideWork": false,
          "payPerSession": "H",
          "summary": "work",
          "riskLevel": "amy",
          "minimumIncentiveNomisCode": "BAS",
          "minimumIncentiveLevel": "BAS",
          "minimumEducationLevel": [],
          "onWing": false,
          "offWing": true,
          "paid": false,
          "capacity": 4,
          "allocated": 4,
          "category": {
            "id": 123,
            "code": "any",
            "name": "any"
          },
          "createdTime": "2023-06-01T09:17:30.425Z",
          "activityState": "LIVE"
        }
      },
      "advanceAttendances": []
    }
  """.trimIndent()
