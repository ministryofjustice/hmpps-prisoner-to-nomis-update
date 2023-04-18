package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.scheduledInstanceMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class SchedulesIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class UpdateScheduleInstances {
    @Test
    fun `should update scheduled instances`() {
      ActivitiesApiExtension.activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubScheduleInstancesUpdate(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(amendScheduledInstancesEvent())

      await untilAsserted { ActivitiesApiExtension.activitiesApi.verify(getRequestedFor(urlEqualTo("/schedules/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted { MappingExtension.mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted {
        NomisApiExtension.nomisApi.verify(
          putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/schedules"))
            .withRequestBody(matchingJsonPath("$[0].date", equalTo("2023-01-13")))
            .withRequestBody(matchingJsonPath("$[0].startTime", equalTo("09:00")))
            .withRequestBody(matchingJsonPath("$[0].endTime", equalTo("10:00")))
            .withRequestBody(matchingJsonPath("$[1].date", equalTo("2023-01-14")))
            .withRequestBody(matchingJsonPath("$[1].startTime", equalTo("14:00")))
            .withRequestBody(matchingJsonPath("$[1].endTime", equalTo("16:30"))),
        )
      }
      assertThat(awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }

    @Test
    fun `should put messages on DLQ if external API call fails`() {
      ActivitiesApiExtension.activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubScheduleInstancesUpdateWithError(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(amendScheduledInstancesEvent())

      await untilAsserted {
        assertThat(
          awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get(),
        ).isEqualTo(1)
      }
    }

    private fun amendScheduledInstancesEvent(): PublishRequest? =
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.scheduled-instances.amended", ACTIVITY_SCHEDULE_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.scheduled-instances.amended").build(),
          ),
        ).build()
  }

  @Nested
  inner class UpdateScheduledInstance {
    @Test
    fun `should update scheduled instance`() {
      ActivitiesApiExtension.activitiesApi.stubGetScheduledInstance(SCHEDULE_INSTANCE_ID, buildGetScheduledInstanceResponse())
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubScheduledInstanceUpdate(NOMIS_CRS_ACTY_ID, """{ "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

      awsSnsClient.publish(amendScheduledInstanceEvent())

      await untilAsserted { ActivitiesApiExtension.activitiesApi.verify(getRequestedFor(urlEqualTo("/scheduled-instances/$SCHEDULE_INSTANCE_ID"))) }
      await untilAsserted { MappingExtension.mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted {
        NomisApiExtension.nomisApi.verify(
          putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/schedule"))
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
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubScheduledInstanceUpdateWithError(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(amendScheduledInstanceEvent())

      await untilAsserted {
        assertThat(
          awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get(),
        ).isEqualTo(1)
      }
    }

    private fun amendScheduledInstanceEvent(): PublishRequest? =
      PublishRequest.builder().topicArn(topicArn)
        .message(scheduledInstanceMessagePayload("activities.scheduled-instance.amended", ACTIVITY_SCHEDULE_ID, SCHEDULE_INSTANCE_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.scheduled-instance.amended").build(),
          ),
        ).build()
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
      "cancelledBy": null
    }
  """.trimIndent()
