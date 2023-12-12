@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

class ActivityResourceIntTest : IntegrationTestBase() {

  @Nested
  inner class SynchroniseCreateActivity {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.post().uri("/activities/1")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/activities/1")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/activities/1")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will synchronise the activity`() = runTest {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubCreateActivity()
      nomisApi.stubActivityCreate(buildNomisActivityResponse())

      webTestClient.post().uri("/activities/$ACTIVITY_SCHEDULE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().isCreated

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("code", equalTo("$ACTIVITY_SCHEDULE_ID"))),
      )
      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
          .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_CREATED"))),
      )
      verify(telemetryClient).trackEvent("activity-create-requested", mapOf("dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-create-success"),
        check {
          assertThat(it).containsEntry("dpsActivityScheduleId", ACTIVITY_SCHEDULE_ID.toString())
          assertThat(it).containsEntry("prisonId", "PVI")
        },
        isNull(),
      )
    }

    @Test
    fun `will synchronise an activity with missing pay rates`() = runTest {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse(payRates = """"pay": [],"""))
      mappingServer.stubGetMappingsWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubCreateActivity()
      nomisApi.stubActivityCreate(buildNomisActivityResponse())

      webTestClient.post().uri("/activities/$ACTIVITY_SCHEDULE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().isCreated

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("code", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("payRates.size()", equalTo("0"))),
      )
    }

    @Test
    fun `will return error if anything fails`() = runTest {
      activitiesApi.stubGetScheduleWithError(ACTIVITY_SCHEDULE_ID)

      webTestClient.post().uri("/activities/$ACTIVITY_SCHEDULE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().is5xxServerError

      verify(telemetryClient).trackEvent("activity-create-requested", mapOf("dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-create-failed"),
        check { assertThat(it).containsEntry("dpsActivityScheduleId", ACTIVITY_SCHEDULE_ID.toString()) },
        isNull(),
      )
    }
  }

  @Nested
  inner class SynchroniseUpdateActivity {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.put().uri("/activities/1")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/activities/1")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/activities/1")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will synchronise the activity`() = runTest {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      nomisApi.stubActivityUpdate(NOMIS_CRS_ACTY_ID, buildNomisActivityResponse())
      mappingServer.stubUpdateActivity()

      webTestClient.put().uri("/activities/$ACTIVITY_SCHEDULE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().isOk

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID")),
      )
      mappingServer.verify(
        putRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
          .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_UPDATED"))),
      )
      verify(telemetryClient).trackEvent("activity-amend-requested", mapOf("dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-success"),
        check {
          assertThat(it).containsEntry("dpsActivityScheduleId", ACTIVITY_SCHEDULE_ID.toString())
          assertThat(it).containsEntry("prisonId", "PVI")
        },
        isNull(),
      )
    }

    @Test
    fun `will synchronise an activity update with missing pay rates`() = runTest {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse(payRates = """"pay": [],"""))
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      nomisApi.stubActivityUpdate(NOMIS_CRS_ACTY_ID, buildNomisActivityResponse())
      mappingServer.stubUpdateActivity()

      webTestClient.put().uri("/activities/$ACTIVITY_SCHEDULE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().isOk

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID"))
          .withRequestBody(matchingJsonPath("payRates.size()", equalTo("0"))),
      )
    }

    @Test
    fun `will return error if anything fails`() = runTest {
      activitiesApi.stubGetScheduleWithError(ACTIVITY_SCHEDULE_ID)

      webTestClient.put().uri("/activities/$ACTIVITY_SCHEDULE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().is5xxServerError

      verify(telemetryClient).trackEvent("activity-amend-requested", mapOf("dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check { assertThat(it).containsEntry("dpsActivityScheduleId", ACTIVITY_SCHEDULE_ID.toString()) },
        isNull(),
      )
    }
  }

  @Nested
  inner class SynchroniseUpsertAllocation {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.put().uri("/allocations/1")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/allocations/1")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/allocations/1")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will synchronise an allocation`() = runTest {
      activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDtoJsonResponse())
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      webTestClient.put().uri("/allocations/$ALLOCATION_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().isOk

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation")),
      )

      verify(telemetryClient).trackEvent("activity-allocation-requested", mapOf("dpsAllocationId" to ALLOCATION_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-allocation-success"),
        check {
          assertThat(it).containsEntry("dpsAllocationId", ALLOCATION_ID.toString())
          assertThat(it).containsEntry("prisonId", "MDI")
        },
        isNull(),
      )
    }

    @Test
    fun `will return error if anything fails`() = runTest {
      activitiesApi.stubGetAllocationWithError(ALLOCATION_ID)

      webTestClient.put().uri("/allocations/$ALLOCATION_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().is5xxServerError

      verify(telemetryClient).trackEvent("activity-allocation-requested", mapOf("dpsAllocationId" to ALLOCATION_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-allocation-failed"),
        check { assertThat(it).containsEntry("dpsAllocationId", ALLOCATION_ID.toString()) },
        isNull(),
      )
    }
  }

  @Nested
  inner class SynchroniseUpsertAttendance {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.put().uri("/attendances/1")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/attendances/1")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/attendances/1")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will synchronise an attendance`() = runTest {
      activitiesApi.stubGetAttendanceSync(ATTENDANCE_ID, buildGetAttendanceSyncResponse())
      mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      nomisApi.stubUpsertAttendance(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID, """{ "eventId": $NOMIS_EVENT_ID, "courseScheduleId": $NOMIS_CRS_SCH_ID, "prisonId": "MDI" }""")

      webTestClient.put().uri("/attendances/$ATTENDANCE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().isOk

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance")),
      )

      verify(telemetryClient).trackEvent("activity-attendance-requested", mapOf("dpsAttendanceId" to ATTENDANCE_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-update-success"),
        check {
          assertThat(it).containsEntry("dpsAttendanceId", ATTENDANCE_ID.toString())
          assertThat(it).containsEntry("prisonId", "MDI")
        },
        isNull(),
      )
    }

    @Test
    fun `will return error if anything fails`() = runTest {
      activitiesApi.stubGetAttendanceSyncWithError(ATTENDANCE_ID)

      webTestClient.put().uri("/attendances/$ATTENDANCE_ID")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ACTIVITIES")))
        .exchange()
        .expectStatus().is5xxServerError

      verify(telemetryClient).trackEvent("activity-attendance-requested", mapOf("dpsAttendanceId" to ATTENDANCE_ID.toString()), null)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-update-failed"),
        check { assertThat(it).containsEntry("dpsAttendanceId", ATTENDANCE_ID.toString()) },
        isNull(),
      )
    }
  }

  @Nested
  inner class DeleteAll {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.delete().uri("/activities")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.delete().uri("/activities")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `create visit forbidden with wrong role`() {
      webTestClient.delete().uri("/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should delete all activities`() {
      mappingServer.stubGetAllActivityMappings(
        """[
           { "activityScheduleId": 101, "nomisCourseActivityId": 201, "mappingType": "ACTIVITY_CREATED", "whenCreated": "2020-01-01T00:00:00Z" },
           { "activityScheduleId": 102, "nomisCourseActivityId": 202, "mappingType": "MIGRATED" }
           ]
        """.trimIndent(),
      )
      mappingServer.stubDeleteActivityMapping(101)
      mappingServer.stubDeleteActivityMapping(102)
      nomisApi.stubActivityDelete(201)
      nomisApi.stubActivityDelete(202)

      webTestClient.delete()
        .uri("/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
        .exchange()
        .expectStatus()
        .isNoContent

      await untilAsserted {
        mappingServer.verify(WireMock.deleteRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/101")))
        mappingServer.verify(WireMock.deleteRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/102")))
        nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/activities/201")))
        nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/activities/202")))
      }
    }
  }
}
