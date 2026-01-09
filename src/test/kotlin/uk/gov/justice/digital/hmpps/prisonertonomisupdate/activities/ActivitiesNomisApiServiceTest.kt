package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertAttendanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.updateActivity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.updateScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.upsertAllocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@SpringAPIServiceTest
@Import(ActivitiesNomisApiService::class)
class ActivitiesNomisApiServiceTest {

  @Autowired
  private lateinit var activitiesNomisApiService: ActivitiesNomisApiService

  @Nested
  inner class CreateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubActivityCreate("""{ "courseActivityId": 456, "courseSchedules": [] }""")

      activitiesNomisApiService.createActivity(uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.newActivity())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      nomisApi.stubActivityCreate("""{ "courseActivityId": 456, "courseSchedules": [] }""")

      activitiesNomisApiService.createActivity(uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.newActivity())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("WWI"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubActivityCreateWithError(503)

      assertThrows<ServiceUnavailable> {
        activitiesNomisApiService.createActivity(uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.newActivity())
      }
    }
  }

  @Nested
  inner class UpdateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubActivityUpdate(1L, """{ "courseActivityId": 1, "courseSchedules": [] }""")

      activitiesNomisApiService.updateActivity(1L, updateActivity())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put data to nomis api`() = runTest {
      nomisApi.stubActivityUpdate(1L, """{ "courseActivityId": 1, "courseSchedules": [] }""")

      activitiesNomisApiService.updateActivity(1L, updateActivity())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1"))
          .withRequestBody(matchingJsonPath("$.startDate", equalTo("2023-02-01")))
          .withRequestBody(matchingJsonPath("$.capacity", equalTo("123")))
          .withRequestBody(matchingJsonPath("$.description", equalTo("updated activity")))
          .withRequestBody(matchingJsonPath("$.minimumIncentiveLevelCode", equalTo("STD")))
          .withRequestBody(matchingJsonPath("$.payPerSession", equalTo("F")))
          .withRequestBody(matchingJsonPath("$.excludeBankHolidays", equalTo("true")))
          .withRequestBody(matchingJsonPath("$.endDate", equalTo("2023-02-10")))
          .withRequestBody(matchingJsonPath("$.internalLocationId", equalTo("703000")))
          .withRequestBody(matchingJsonPath("$.programCode", equalTo("PROGRAM_SERVICE")))
          .withRequestBody(matchingJsonPath("$.outsideWork", equalTo("true"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubActivityUpdateWithError(1, 503)

      assertThrows<ServiceUnavailable> {
        activitiesNomisApiService.updateActivity(1L, updateActivity())
      }
    }
  }

  @Nested
  inner class UpdateScheduledInstance {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubScheduledInstanceUpdate(1L, """{ "courseScheduleId": 2 }""")

      activitiesNomisApiService.updateScheduledInstance(1L, updateScheduledInstance())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1/schedule"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put data to nomis api`() = runTest {
      nomisApi.stubScheduleInstancesUpdate(1L)

      activitiesNomisApiService.updateScheduledInstance(1L, updateScheduledInstance())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1/schedule"))
          .withRequestBody(matchingJsonPath("date", equalTo("2023-02-10")))
          .withRequestBody(matchingJsonPath("startTime", equalTo("08:00")))
          .withRequestBody(matchingJsonPath("endTime", equalTo("11:00")))
          .withRequestBody(matchingJsonPath("cancelled", equalTo("true"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubScheduledInstanceUpdateWithError(1, 503)

      assertThrows<ServiceUnavailable> {
        activitiesNomisApiService.updateScheduledInstance(1L, updateScheduledInstance())
      }
    }
  }

  @Nested
  inner class UpsertAllocation {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAllocationUpsert(12)

      activitiesNomisApiService.upsertAllocation(12, upsertAllocation())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/allocation"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will send data to nomis api`() = runTest {
      nomisApi.stubAllocationUpsert(12)

      activitiesNomisApiService.upsertAllocation(12, upsertAllocation())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/allocation"))
          .withRequestBody(matchingJsonPath("$.bookingId", equalTo("456"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubAllocationUpsertWithError(12, 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.upsertAllocation(12, upsertAllocation())
      }
    }
  }

  @Nested
  inner class UpsertAttendance {

    private val validResponse = """{
        "eventId": 1,
        "courseScheduleId": 2,
        "created": true,
        "prisonId": "MDI"
      }
    """.trimMargin()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubUpsertAttendance(11, 22, validResponse)

      activitiesNomisApiService.upsertAttendance(11, 22, newAttendance())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/11/booking/22/attendance"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      nomisApi.stubUpsertAttendance(11, 22, validResponse)

      activitiesNomisApiService.upsertAttendance(11, 22, newAttendance())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/11/booking/22/attendance"))
          .withRequestBody(matchingJsonPath("$.scheduleDate", equalTo("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("$.startTime", equalTo("11:00")))
          .withRequestBody(matchingJsonPath("$.endTime", equalTo("13:00")))
          .withRequestBody(matchingJsonPath("$.eventStatusCode", equalTo("COMP")))
          .withRequestBody(matchingJsonPath("$.eventOutcomeCode", equalTo("ACCAB")))
          .withRequestBody(matchingJsonPath("$.comments", equalTo("Prisoner was too unwell to attend the activity.")))
          .withRequestBody(matchingJsonPath("$.unexcusedAbsence", equalTo("false")))
          .withRequestBody(matchingJsonPath("$.authorisedAbsence", equalTo("true")))
          .withRequestBody(matchingJsonPath("$.paid", equalTo("true"))),
      )
    }

    @Test
    fun `will parse the response`() = runTest {
      nomisApi.stubUpsertAttendance(11, 22, validResponse)

      val response = activitiesNomisApiService.upsertAttendance(11, 22, newAttendance())

      with(response) {
        assertThat(eventId).isEqualTo(1)
        assertThat(courseScheduleId).isEqualTo(2)
        assertThat(created).isTrue()
      }
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubUpsertAttendanceWithError(11, 22, 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.upsertAttendance(11, 22, newAttendance())
      }
    }
  }

  @Nested
  inner class DeleteAttendance {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubDeleteAttendance(11, 22)

      activitiesNomisApiService.deleteAttendance(11, 22)

      nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/schedules/11/booking/22/attendance"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubDeleteAttendanceWithError(11, 22, 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.deleteAttendance(11, 22)
      }
    }
  }

  @Nested
  inner class AllocationReconciliation {

    private fun jsonResponse(prisonId: String = "BXI") = """
      {
        "prisonId": "$prisonId",
        "bookings": [
          {
            "bookingId": 1234,
            "count": 2
          },
          {
            "bookingId": 1235,
            "count": 1
          }
        ]
      }
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAllocationReconciliation("BXI", jsonResponse())

      activitiesNomisApiService.getAllocationReconciliation("BXI")

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/allocations/reconciliation/BXI"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubAllocationReconciliation("BXI", jsonResponse())

      val response = activitiesNomisApiService.getAllocationReconciliation("BXI")

      with(response) {
        assertThat(prisonId).isEqualTo("BXI")
        assertThat(bookings[0].bookingId).isEqualTo(1234)
        assertThat(bookings[0].count).isEqualTo(2)
        assertThat(bookings[1].bookingId).isEqualTo(1235)
        assertThat(bookings[1].count).isEqualTo(1)
      }
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubAllocationReconciliationWithError("BXI", 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.getAllocationReconciliation("BXI")
      }
    }
  }

  @Nested
  inner class SuspendedAllocationReconciliation {

    private fun jsonResponse(prisonId: String = "BXI") = """
      {
        "prisonId": "$prisonId",
        "bookings": [
          {
            "bookingId": 1234,
            "count": 2
          },
          {
            "bookingId": 1235,
            "count": 1
          }
        ]
      }
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubSuspendedAllocationReconciliation("BXI", jsonResponse())

      activitiesNomisApiService.getSuspendedAllocationReconciliation("BXI")

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/allocations/reconciliation/BXI"))
          .withQueryParam("suspended", equalTo("true"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubSuspendedAllocationReconciliation("BXI", jsonResponse())

      val response = activitiesNomisApiService.getSuspendedAllocationReconciliation("BXI")

      with(response) {
        assertThat(prisonId).isEqualTo("BXI")
        assertThat(bookings[0].bookingId).isEqualTo(1234)
        assertThat(bookings[0].count).isEqualTo(2)
        assertThat(bookings[1].bookingId).isEqualTo(1235)
        assertThat(bookings[1].count).isEqualTo(1)
      }
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubSuspendedAllocationReconciliationWithError("BXI", 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.getSuspendedAllocationReconciliation("BXI")
      }
    }
  }

  @Nested
  inner class AttendanceReconciliation {

    private fun jsonResponse(prisonId: String = "BXI", date: LocalDate = LocalDate.now()) = """
      {
        "prisonId": "$prisonId",
        "date": "$date",
        "bookings": [
          {
            "bookingId": 1234,
            "count": 2
          },
          {
            "bookingId": 1235,
            "count": 1
          }
        ]
      }
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAttendanceReconciliation("BXI", LocalDate.now(), jsonResponse())

      activitiesNomisApiService.getAttendanceReconciliation("BXI", LocalDate.now())

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/attendances/reconciliation/BXI?date=${LocalDate.now()}"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubAttendanceReconciliation("BXI", LocalDate.now(), jsonResponse())

      val response = activitiesNomisApiService.getAttendanceReconciliation("BXI", LocalDate.now())

      with(response) {
        assertThat(prisonId).isEqualTo("BXI")
        assertThat(bookings[0].bookingId).isEqualTo(1234)
        assertThat(bookings[0].count).isEqualTo(2)
        assertThat(bookings[1].bookingId).isEqualTo(1235)
        assertThat(bookings[1].count).isEqualTo(1)
      }
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubAttendanceReconciliationWithError("BXI", LocalDate.now(), 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.getAttendanceReconciliation("BXI", LocalDate.now())
      }
    }
  }

  @Nested
  inner class GetServiceAgencies {

    private fun jsonResponse() = """
      [
        {
          "agencyId": "BXI",
          "name": "Brixton"
        },
        {
          "agencyId": "MDI",
          "name": "Moorland"
        }
      ]
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetServiceAgencies("ACTIVITY", jsonResponse())

      activitiesNomisApiService.getServiceAgencies("ACTIVITY")

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/agency-switches/ACTIVITY"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubGetServiceAgencies("ACTIVITY", jsonResponse())

      val response = activitiesNomisApiService.getServiceAgencies("ACTIVITY")

      assertThat(response.size).isEqualTo(2)
      assertThat(response[0].agencyId).isEqualTo("BXI")
      assertThat(response[0].name).isEqualTo("Brixton")
      assertThat(response[1].agencyId).isEqualTo("MDI")
      assertThat(response[1].name).isEqualTo("Moorland")
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubGetServiceAgenciesWithError("ACTIVITY", 400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.getServiceAgencies("ACTIVITY")
      }
    }
  }

  @Nested
  inner class GetPrisonerDetails {

    private fun jsonResponse() = """
      [
        {
          "bookingId": 1,
          "offenderNo": "A1234AA",
          "location": "BXI",
          "offenderId": 1234,
          "active": true
        },
        {
          "bookingId": 2,
          "offenderNo": "A1234BB",
          "location": "OUT",
          "offenderId": 1232,
          "active": false
        }
      ]
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetActivitiesPrisonerDetails(jsonResponse())

      activitiesNomisApiService.getPrisonerDetails(listOf(1, 2))

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/bookings"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass booking ids in body`() = runTest {
      nomisApi.stubGetActivitiesPrisonerDetails(jsonResponse())

      activitiesNomisApiService.getPrisonerDetails(listOf(1, 2))

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/bookings"))
          .withRequestBody(matchingJsonPath("$[0]", equalTo("1")))
          .withRequestBody(matchingJsonPath("$[1]", equalTo("2"))),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubGetActivitiesPrisonerDetails(jsonResponse())

      val response = activitiesNomisApiService.getPrisonerDetails(listOf(1, 2))

      assertThat(response.size).isEqualTo(2)
      assertThat(response[0].bookingId).isEqualTo(1)
      assertThat(response[0].offenderNo).isEqualTo("A1234AA")
      assertThat(response[0].location).isEqualTo("BXI")
      assertThat(response[1].bookingId).isEqualTo(2)
      assertThat(response[1].offenderNo).isEqualTo("A1234BB")
      assertThat(response[1].location).isEqualTo("OUT")
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubGetPrisonerDetailsWithError(400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.getPrisonerDetails(listOf(1, 2))
      }
    }
  }

  @Nested
  inner class GetMaxCourseScheduleId {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetMaxCourseScheduleId(123)

      activitiesNomisApiService.getMaxCourseScheduleId()

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/schedules/max-id"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubGetMaxCourseScheduleId(123)

      val response = activitiesNomisApiService.getMaxCourseScheduleId()

      assertThat(response).isEqualTo(123)
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubGetMaxCourseScheduleIdWithError(400)

      assertThrows<BadRequest> {
        activitiesNomisApiService.getMaxCourseScheduleId()
      }
    }
  }
}

private fun newAttendance() = UpsertAttendanceRequest(
  scheduleDate = LocalDate.now(),
  startTime = "11:00",
  endTime = "13:00",
  eventStatusCode = "COMP",
  eventOutcomeCode = "ACCAB",
  comments = "Prisoner was too unwell to attend the activity.",
  unexcusedAbsence = false,
  authorisedAbsence = true,
  paid = true,
)
