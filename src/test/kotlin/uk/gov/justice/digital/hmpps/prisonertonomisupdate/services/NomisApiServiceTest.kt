@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ChargeToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourseScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.EvidenceToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ExistingHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.HearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.IncidentToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.RepairToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UnquashHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateEvidenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@SpringAPIServiceTest
@Import(NomisApiService::class)
internal class NomisApiServiceTest {

  @Autowired
  private lateinit var nomisApiService: NomisApiService

  @Nested
  inner class CreateVisit {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post visit data to nomis api`() = runTest {
      nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit(offenderNo = "AB123D"))

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withRequestBody(matchingJsonPath("$.offenderNo", equalTo("AB123D"))),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      nomisApi.stubVisitCreateWithError("AB123D", 404)

      assertThrows<NotFound> {
        nomisApiService.createVisit(newVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubVisitCreateWithError("AB123D", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createVisit(newVisit())
      }
    }
  }

  @Nested
  inner class CancelVisit {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubVisitCancel(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApiService.cancelVisit(cancelVisit())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post cancel request to nomis api`() = runTest {
      nomisApiService.cancelVisit(cancelVisit())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel")),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      nomisApi.stubVisitCancelWithError("AB123D", "12", 404)

      assertThrows<NotFound> {
        nomisApiService.cancelVisit(cancelVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubVisitCancelWithError("AB123D", "12", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.cancelVisit(cancelVisit())
      }
    }
  }

  @Nested
  inner class UpdateVisit {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubVisitUpdate(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post cancel request to nomis api`() = runTest {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12")),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      nomisApi.stubVisitUpdateWithError("AB123D", "12", 404)

      assertThrows<NotFound> {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubVisitUpdateWithError("AB123D", "12", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }
    }
  }

  @Nested
  inner class CreateIncentive {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("MDI"))),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      nomisApi.stubIncentiveCreateWithError(456, 404)

      assertThrows<NotFound> {
        nomisApiService.createIncentive(456, newIncentive())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubIncentiveCreateWithError(456, 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createIncentive(456, newIncentive())
      }
    }
  }

  @Nested
  inner class GetCurrentIncentive {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubCurrentIncentiveGet(99, "STD")
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      nomisApiService.getCurrentIncentive(99)

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/incentives/booking-id/99/current"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `get parse core data`() = runTest {
      val incentive = nomisApiService.getCurrentIncentive(99)

      assertThat(incentive?.iepLevel?.code).isEqualTo("STD")
    }

    @Test
    internal fun `when incentive is not found level will be null`() = runTest {
      val incentive = nomisApiService.getCurrentIncentive(88)

      assertThat(incentive).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubCurrentIncentiveGetWithError(99, responseCode = 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.getCurrentIncentive(99)
      }
    }
  }

  @Nested
  inner class CreateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubActivityCreate("""{ "courseActivityId": 456, "courseSchedules": [] }""")

      nomisApiService.createActivity(newActivity())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      nomisApi.stubActivityCreate("""{ "courseActivityId": 456, "courseSchedules": [] }""")

      nomisApiService.createActivity(newActivity())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("WWI"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubActivityCreateWithError(503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createActivity(newActivity())
      }
    }
  }

  @Nested
  inner class UpdateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubActivityUpdate(1L, """{ "courseActivityId": 1, "courseSchedules": [] }""")

      nomisApiService.updateActivity(1L, updateActivity())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put data to nomis api`() = runTest {
      nomisApi.stubActivityUpdate(1L, """{ "courseActivityId": 1, "courseSchedules": [] }""")

      nomisApiService.updateActivity(1L, updateActivity())

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
        nomisApiService.updateActivity(1L, updateActivity())
      }
    }
  }

  @Nested
  inner class UpdateScheduledInstance {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubScheduledInstanceUpdate(1L, """{ "courseScheduleId": 2 }""")

      nomisApiService.updateScheduledInstance(1L, updateScheduledInstance())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1/schedule"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put data to nomis api`() = runTest {
      nomisApi.stubScheduleInstancesUpdate(1L)

      nomisApiService.updateScheduledInstance(1L, updateScheduledInstance())

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
        nomisApiService.updateScheduledInstance(1L, updateScheduledInstance())
      }
    }
  }

  @Nested
  inner class UpsertAllocation {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAllocationUpsert(12)

      nomisApiService.upsertAllocation(12, upsertAllocation())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/allocation"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will send data to nomis api`() = runTest {
      nomisApi.stubAllocationUpsert(12)

      nomisApiService.upsertAllocation(12, upsertAllocation())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/allocation"))
          .withRequestBody(matchingJsonPath("$.bookingId", equalTo("456"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubAllocationUpsertWithError(12, 400)

      assertThrows<BadRequest> {
        nomisApiService.upsertAllocation(12, upsertAllocation())
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

      nomisApiService.upsertAttendance(11, 22, newAttendance())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/11/booking/22/attendance"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      nomisApi.stubUpsertAttendance(11, 22, validResponse)

      nomisApiService.upsertAttendance(11, 22, newAttendance())

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

      val response = nomisApiService.upsertAttendance(11, 22, newAttendance())

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
        nomisApiService.upsertAttendance(11, 22, newAttendance())
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

      nomisApiService.getAllocationReconciliation("BXI")

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/allocations/reconciliation/BXI"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubAllocationReconciliation("BXI", jsonResponse())

      val response = nomisApiService.getAllocationReconciliation("BXI")

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
        nomisApiService.getAllocationReconciliation("BXI")
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

      nomisApiService.getAttendanceReconciliation("BXI", LocalDate.now())

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/attendances/reconciliation/BXI?date=${LocalDate.now()}"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubAttendanceReconciliation("BXI", LocalDate.now(), jsonResponse())

      val response = nomisApiService.getAttendanceReconciliation("BXI", LocalDate.now())

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
        nomisApiService.getAttendanceReconciliation("BXI", LocalDate.now())
      }
    }
  }

  @Nested
  inner class GetServicePrisons {

    private fun jsonResponse() = """
      [
        {
          "prisonId": "BXI",
          "name": "Brixton"
        },
        {
          "prisonId": "MDI",
          "name": "Moorland"
        }
      ]
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetServicePrisons("ACTIVITY", jsonResponse())

      nomisApiService.getServicePrisons("ACTIVITY")

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/service-prisons/ACTIVITY"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubGetServicePrisons("ACTIVITY", jsonResponse())

      val response = nomisApiService.getServicePrisons("ACTIVITY")

      assertThat(response.size).isEqualTo(2)
      assertThat(response[0].prisonId).isEqualTo("BXI")
      assertThat(response[0].name).isEqualTo("Brixton")
      assertThat(response[1].prisonId).isEqualTo("MDI")
      assertThat(response[1].name).isEqualTo("Moorland")
    }

    @Test
    fun `should throw exception on error`() = runTest {
      nomisApi.stubGetServicePrisonsWithError("ACTIVITY", 400)

      assertThrows<BadRequest> {
        nomisApiService.getServicePrisons("ACTIVITY")
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
          "location": "BXI"
        },
        {
          "bookingId": 2,
          "offenderNo": "A1234BB",
          "location": "OUT"
        }
      ]
    """.trimIndent()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetPrisonerDetails(jsonResponse())

      nomisApiService.getPrisonerDetails(listOf(1, 2))

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/bookings"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass booking ids in body`() = runTest {
      nomisApi.stubGetPrisonerDetails(jsonResponse())

      nomisApiService.getPrisonerDetails(listOf(1, 2))

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/bookings"))
          .withRequestBody(matchingJsonPath("$[0]", equalTo("1")))
          .withRequestBody(matchingJsonPath("$[1]", equalTo("2"))),
      )
    }

    @Test
    fun `should parse response`() = runTest {
      nomisApi.stubGetPrisonerDetails(jsonResponse())

      val response = nomisApiService.getPrisonerDetails(listOf(1, 2))

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
        nomisApiService.getPrisonerDetails(listOf(1, 2))
      }
    }
  }

  @Nested
  inner class CreateAppointment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubAppointmentCreate("""{ "id": 12345 }""")

      nomisApiService.createAppointment(newAppointment())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/appointments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`(): Unit = runTest {
      nomisApi.stubAppointmentCreate("""{ "id": 12345 }""")

      nomisApiService.createAppointment(newAppointment())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/appointments"))
          .withRequestBody(matchingJsonPath("$.internalLocationId", equalTo("703000"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubAppointmentCreateWithError()

      assertThrows<InternalServerError> {
        nomisApiService.createAppointment(newAppointment())
      }
    }
  }

  @Nested
  inner class GetSentencingAdjustments {
    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetSentencingAdjustments(123456)

      nomisApiService.getAdjustments(123456)

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/prisoners/booking-id/123456/sentencing-adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw exception on any error`() = runTest {
      nomisApi.stubGetSentencingAdjustmentsWithError(123456, status = 404)

      assertThrows<NotFound> {
        nomisApiService.getAdjustments(123456)
      }
    }
  }

  @Nested
  inner class CreateSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubSentenceAdjustmentCreate(bookingId = 12345, sentenceSequence = 2)

      nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/sentences/2/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post sentence adjustment data to nomis api`() = runTest {
      nomisApi.stubSentenceAdjustmentCreate(bookingId = 12345, sentenceSequence = 2)

      nomisApiService.createSentenceAdjustment(
        bookingId = 12345,
        sentenceSequence = 2,
        request = newSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFromDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand",
        ),
      )

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/sentences/2/adjustments"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
      )
    }

    @Test
    internal fun `when booking or sentence is not found an exception is thrown`() = runTest {
      nomisApi.stubSentenceAdjustmentCreateWithError(
        bookingId = 12345,
        sentenceSequence = 2,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubSentenceAdjustmentCreateWithError(
        bookingId = 12345,
        sentenceSequence = 2,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment())
      }
    }
  }

  @Nested
  inner class UpdateSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubSentenceAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put sentence adjustment data to nomis api`() = runTest {
      nomisApi.stubSentenceAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateSentenceAdjustment(
        98765,
        request = updateSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFromDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand",
          sentenceSequence = 3,
        ),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("sentenceSequence", equalTo("3")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
      )
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() = runTest {
      nomisApi.stubSentenceAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubSentenceAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment())
      }
    }
  }

  @Nested
  inner class DeleteSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubSentenceAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteSentenceAdjustment(98765)

      nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will delete sentence adjustment from nomis`() = runTest {
      nomisApi.stubSentenceAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteSentenceAdjustment(98765)

      nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/sentence-adjustments/98765")),
      )
    }

    @Test
    internal fun `if 404 - which is not expected - is returned an exception is thrown`() = runTest {
      nomisApi.stubSentenceAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.deleteSentenceAdjustment(98765)
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubSentenceAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.deleteSentenceAdjustment(98765)
      }
    }
  }

  @Nested
  inner class CreateKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubKeyDateAdjustmentCreate(bookingId = 12345)

      nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post key date adjustment data to nomis api`() = runTest {
      nomisApi.stubKeyDateAdjustmentCreate(bookingId = 12345)

      nomisApiService.createKeyDateAdjustment(
        bookingId = 12345,
        request = newSentencingAdjustment(
          adjustmentTypeCode = "ADA",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
        ),
      )

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/adjustments"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9"))),
      )
    }

    @Test
    internal fun `when booking is not found an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentCreateWithError(
        bookingId = 12345,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentCreateWithError(
        bookingId = 12345,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment())
      }
    }
  }

  @Nested
  inner class UpdateKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment())

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put key date adjustment data to nomis api`() = runTest {
      nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateKeyDateAdjustment(
        98765,
        request = updateSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFromDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand",
        ),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("sentenceSequence", absent()))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
      )
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment())
      }
    }
  }

  @Nested
  inner class DeleteKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubKeyDateAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteKeyDateAdjustment(98765)

      nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will delete key date adjustment from nomis`() = runTest {
      nomisApi.stubKeyDateAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteKeyDateAdjustment(98765)

      nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/key-date-adjustments/98765")),
      )
    }

    @Test
    internal fun `if 404 - which is not expected - is returned an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.deleteKeyDateAdjustment(98765)
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.deleteKeyDateAdjustment(98765)
      }
    }
  }

  @Nested
  inner class CreateAdjudication {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationCreate("AB123D")

      nomisApiService.createAdjudication("AB123D", newAdjudication())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/adjudications"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post adjudication data to nomis api`() = runTest {
      nomisApi.stubAdjudicationCreate("AB123D", adjudicationNumber = 1234567)

      nomisApiService.createAdjudication("AB123D", newAdjudication())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/adjudications"))
          .withRequestBody(matchingJsonPath("$.incident.details", equalTo("The details of the incident are as follows"))),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationCreateWithError("AB123D", 404)

      assertThrows<NotFound> {
        nomisApiService.createAdjudication("AB123D", newAdjudication())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationCreateWithError("AB123D", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createAdjudication("AB123D", newAdjudication())
      }
    }
  }

  @Nested
  inner class UpdateAdjudicationRepairs {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationRepairsUpdate(1234567)

      nomisApiService.updateAdjudicationRepairs(
        1234567,
        UpdateRepairsRequest(repairs = listOf(RepairToUpdateOrAdd(RepairToUpdateOrAdd.TypeCode.CLEA, "cleaning required"))),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/repairs"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post repair data to nomis api`() = runTest {
      nomisApi.stubAdjudicationRepairsUpdate(1234567)

      nomisApiService.updateAdjudicationRepairs(
        1234567,
        UpdateRepairsRequest(repairs = listOf(RepairToUpdateOrAdd(RepairToUpdateOrAdd.TypeCode.CLEA, "cleaning required"))),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/repairs"))
          .withRequestBody(matchingJsonPath("repairs[0].typeCode", equalTo("CLEA")))
          .withRequestBody(matchingJsonPath("repairs[0].comment", equalTo("cleaning required"))),
      )
    }

    @Test
    fun `when adjudication is not found an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationRepairsUpdateWithError(1234567, 404)

      assertThrows<NotFound> {
        nomisApiService.updateAdjudicationRepairs(
          1234567,
          UpdateRepairsRequest(repairs = listOf(RepairToUpdateOrAdd(RepairToUpdateOrAdd.TypeCode.CLEA, "cleaning required"))),
        )
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationRepairsUpdateWithError(1234567, 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateAdjudicationRepairs(
          1234567,
          UpdateRepairsRequest(repairs = listOf(RepairToUpdateOrAdd(RepairToUpdateOrAdd.TypeCode.CLEA, "cleaning required"))),
        )
      }
    }
  }

  @Nested
  inner class UpdateAdjudicationEvidence {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationEvidenceUpdate(1234567)

      nomisApiService.updateAdjudicationEvidence(
        1234567,
        UpdateEvidenceRequest(evidence = listOf(EvidenceToUpdateOrAdd(EvidenceToUpdateOrAdd.TypeCode.PHOTO, "picture of knife"))),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/evidence"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post evidence data to nomis api`() = runTest {
      nomisApi.stubAdjudicationEvidenceUpdate(1234567)

      nomisApiService.updateAdjudicationEvidence(
        1234567,
        UpdateEvidenceRequest(evidence = listOf(EvidenceToUpdateOrAdd(EvidenceToUpdateOrAdd.TypeCode.PHOTO, "picture of knife"))),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/evidence"))
          .withRequestBody(matchingJsonPath("evidence[0].typeCode", equalTo("PHOTO")))
          .withRequestBody(matchingJsonPath("evidence[0].detail", equalTo("picture of knife"))),
      )
    }

    @Test
    fun `when adjudication is not found an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationEvidenceUpdateWithError(1234567, 404)

      assertThrows<NotFound> {
        nomisApiService.updateAdjudicationEvidence(
          1234567,
          UpdateEvidenceRequest(evidence = listOf(EvidenceToUpdateOrAdd(EvidenceToUpdateOrAdd.TypeCode.PHOTO, "picture of knife"))),
        )
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationEvidenceUpdateWithError(1234567, 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateAdjudicationEvidence(
          1234567,
          UpdateEvidenceRequest(evidence = listOf(EvidenceToUpdateOrAdd(EvidenceToUpdateOrAdd.TypeCode.PHOTO, "picture of knife"))),
        )
      }
    }
  }

  @Nested
  inner class CreateAdjudicationAwards {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationAwardsCreate(1234567, 1)

      val result = nomisApiService.createAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
        request = CreateHearingResultAwardRequest(
          awards = listOf(
            HearingResultAwardRequest(
              sanctionType = HearingResultAwardRequest.SanctionType.ADA,
              sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
              effectiveDate = LocalDate.parse("2020-07-19"),
            ),
          ),
        ),
      )

      assertThat(result).isNotNull

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/awards"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post awards data to nomis api`() = runTest {
      nomisApi.stubAdjudicationAwardsCreate(1234567, 1)

      nomisApiService.createAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
        request = CreateHearingResultAwardRequest(
          awards = listOf(
            HearingResultAwardRequest(
              sanctionType = HearingResultAwardRequest.SanctionType.ADA,
              sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
              sanctionDays = 28,
              effectiveDate = LocalDate.parse("2020-07-19"),
            ),
          ),
        ),
      )

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/awards"))
          .withRequestBody(matchingJsonPath("awards[0].sanctionType", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("awards[0].sanctionStatus", equalTo("IMMEDIATE")))
          .withRequestBody(matchingJsonPath("awards[0].sanctionDays", equalTo("28")))
          .withRequestBody(matchingJsonPath("awards[0].effectiveDate", equalTo("2020-07-19"))),
      )
    }

    @Test
    fun `when adjudication is not found an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationAwardsCreateWithError(adjudicationNumber = 1234567, chargeSequence = 1, status = 404)

      assertThrows<NotFound> {
        nomisApiService.createAdjudicationAwards(
          adjudicationNumber = 1234567,
          chargeSequence = 1,
          request = CreateHearingResultAwardRequest(
            awards = listOf(
              HearingResultAwardRequest(
                sanctionType = HearingResultAwardRequest.SanctionType.ADA,
                sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
                effectiveDate = LocalDate.parse("2020-07-19"),
              ),
            ),
          ),
        )
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationAwardsCreateWithError(adjudicationNumber = 1234567, chargeSequence = 1, status = 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createAdjudicationAwards(
          adjudicationNumber = 1234567,
          chargeSequence = 1,
          request = CreateHearingResultAwardRequest(
            awards = listOf(
              HearingResultAwardRequest(
                sanctionType = HearingResultAwardRequest.SanctionType.ADA,
                sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
                effectiveDate = LocalDate.parse("2020-07-19"),
              ),
            ),
          ),
        )
      }
    }
  }

  @Nested
  inner class UpdateAdjudicationAwards {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationAwardsUpdate(1234567, 1)

      val result = nomisApiService.updateAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
        request = UpdateHearingResultAwardRequest(
          awardsToCreate = listOf(
            HearingResultAwardRequest(
              sanctionType = HearingResultAwardRequest.SanctionType.ADA,
              sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
              effectiveDate = LocalDate.parse("2020-07-19"),
            ),
          ),
          awardsToUpdate = emptyList(),
        ),
      )

      assertThat(result).isNotNull

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/awards"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post awards data to nomis api`() = runTest {
      nomisApi.stubAdjudicationAwardsUpdate(1234567, 1)

      nomisApiService.updateAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
        request = UpdateHearingResultAwardRequest(
          awardsToCreate = listOf(
            HearingResultAwardRequest(
              sanctionType = HearingResultAwardRequest.SanctionType.ADA,
              sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
              sanctionDays = 28,
              effectiveDate = LocalDate.parse("2020-07-19"),
            ),
          ),
          awardsToUpdate = listOf(
            ExistingHearingResultAwardRequest(
              award =
              HearingResultAwardRequest(
                sanctionType = HearingResultAwardRequest.SanctionType.ADA,
                sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
                sanctionDays = 28,
                effectiveDate = LocalDate.parse("2020-07-19"),
              ),
              sanctionSequence = 1,
            ),
          ),
        ),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/awards"))
          .withRequestBody(matchingJsonPath("awardsToCreate[0].sanctionType", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("awardsToCreate[0].sanctionStatus", equalTo("IMMEDIATE")))
          .withRequestBody(matchingJsonPath("awardsToCreate[0].sanctionDays", equalTo("28")))
          .withRequestBody(matchingJsonPath("awardsToCreate[0].effectiveDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("awardsToUpdate[0].sanctionSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.sanctionType", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.sanctionStatus", equalTo("IMMEDIATE")))
          .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.sanctionDays", equalTo("28")))
          .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.effectiveDate", equalTo("2020-07-19"))),
      )
    }

    @Test
    fun `when adjudication is not found an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationAwardsUpdateWithError(adjudicationNumber = 1234567, chargeSequence = 1, status = 404)

      assertThrows<NotFound> {
        nomisApiService.updateAdjudicationAwards(
          adjudicationNumber = 1234567,
          chargeSequence = 1,
          request = UpdateHearingResultAwardRequest(
            awardsToCreate = emptyList(),
            awardsToUpdate = emptyList(),
          ),
        )
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubAdjudicationAwardsUpdateWithError(adjudicationNumber = 1234567, chargeSequence = 1, status = 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateAdjudicationAwards(
          adjudicationNumber = 1234567,
          chargeSequence = 1,
          request = UpdateHearingResultAwardRequest(
            awardsToCreate = emptyList(),
            awardsToUpdate = emptyList(),
          ),
        )
      }
    }
  }

  @Nested
  inner class QuashAdjudicationAwards {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationSquashAwards(1234567, 1)

      nomisApiService.quashAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/quash"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class UnquashAdjudicationAwards {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubAdjudicationUnquashAwards(1234567, 1)

      val result = nomisApiService.unquashAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
        request = UnquashHearingResultAwardRequest(
          findingCode = "PROVED",
          awards = UpdateHearingResultAwardRequest(
            awardsToUpdate = listOf(
              ExistingHearingResultAwardRequest(
                award = HearingResultAwardRequest(
                  sanctionType = HearingResultAwardRequest.SanctionType.ADA,
                  sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
                  sanctionDays = 28,
                  effectiveDate = LocalDate.parse("2020-07-19"),
                ),
                sanctionSequence = 10,
              ),
            ),
            awardsToCreate = emptyList(),
          ),
        ),
      )

      assertThat(result).isNotNull

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/unquash"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post awards and result data to nomis api`() = runTest {
      nomisApi.stubAdjudicationUnquashAwards(1234567, 1)

      nomisApiService.unquashAdjudicationAwards(
        adjudicationNumber = 1234567,
        chargeSequence = 1,
        request = UnquashHearingResultAwardRequest(
          findingCode = "PROVED",
          awards = UpdateHearingResultAwardRequest(
            awardsToUpdate = listOf(
              ExistingHearingResultAwardRequest(
                award = HearingResultAwardRequest(
                  sanctionType = HearingResultAwardRequest.SanctionType.ADA,
                  sanctionStatus = HearingResultAwardRequest.SanctionStatus.IMMEDIATE,
                  sanctionDays = 28,
                  effectiveDate = LocalDate.parse("2020-07-19"),
                ),
                sanctionSequence = 10,
              ),
            ),
            awardsToCreate = emptyList(),
          ),
        ),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/adjudications/adjudication-number/1234567/charge/1/unquash"))
          .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionType", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionStatus", equalTo("IMMEDIATE")))
          .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionDays", equalTo("28")))
          .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.effectiveDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].sanctionSequence", equalTo("10")))
          .withRequestBody(matchingJsonPath("findingCode", equalTo("PROVED"))),
      )
    }
  }

  @Nested
  inner class GetAdaAwardSummary {
    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetAdaAwardSummary(123456)

      nomisApiService.getAdaAwardsSummary(123456)

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/prisoners/booking-id/123456/awards/ada/summary"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw exception on any error`() = runTest {
      nomisApi.stubGetAdaAwardSummaryWithError(123456, status = 404)

      assertThrows<NotFound> {
        nomisApiService.getAdaAwardsSummary(123456)
      }
    }
  }

  // /////////////////////////// LOCATIONS ////////////////////////////

  @Nested
  inner class CreateLocation {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubLocationCreate("""{ "locationId": 12345 }""")

      nomisApiService.createLocation(newLocation())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/locations"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`(): Unit = runTest {
      nomisApi.stubLocationCreate("""{ "locationId": 12345 }""")

      nomisApiService.createLocation(newLocation())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/locations"))
          .withRequestBody(matchingJsonPath("$.locationCode", equalTo("FAIT"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      nomisApi.stubLocationCreateWithError()

      assertThrows<InternalServerError> {
        nomisApiService.createLocation(newLocation())
      }
    }
  }
}

fun newVisit(offenderNo: String = "AB123D"): CreateVisitDto = CreateVisitDto(
  offenderNo = offenderNo,
  prisonId = "MDI",
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  visitType = "SCON",
  issueDate = LocalDate.now(),
  visitComment = "VSIP ref: 123",
  visitOrderComment = "VO VSIP ref: 123",
  room = "Main visits room",
  openClosedStatus = "CLOSED",
)

fun cancelVisit(): CancelVisitDto = CancelVisitDto(offenderNo = "AB123D", nomisVisitId = "12", outcome = "VISCANC")
fun updateVisit(): UpdateVisitDto = UpdateVisitDto(
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  room = "Main visits room",
  openClosedStatus = "CLOSED",
)

fun newIncentive() = CreateIncentiveDto(
  iepDateTime = LocalDateTime.now(),
  prisonId = "MDI",
  iepLevel = "High",
)

fun newActivity() = CreateActivityRequest(
  code = "code",
  startDate = LocalDate.now(),
  prisonId = "WWI",
  internalLocationId = 703000,
  capacity = 14,
  payRates = emptyList(),
  description = "the description",
  programCode = "IRS",
  payPerSession = CreateActivityRequest.PayPerSession.H,
  schedules = listOf(),
  scheduleRules = emptyList(),
  excludeBankHolidays = true,
  minimumIncentiveLevelCode = "STD",
  outsideWork = true,
)

fun updateActivity() = UpdateActivityRequest(
  startDate = LocalDate.parse("2023-02-01"),
  capacity = 123,
  payRates = emptyList(),
  description = "updated activity",
  minimumIncentiveLevelCode = "STD",
  payPerSession = UpdateActivityRequest.PayPerSession.F,
  scheduleRules = emptyList(),
  schedules = emptyList(),
  excludeBankHolidays = true,
  endDate = LocalDate.parse("2023-02-10"),
  internalLocationId = 703000,
  programCode = "PROGRAM_SERVICE",
  outsideWork = true,
)

fun updateScheduledInstance() =
  CourseScheduleRequest(
    date = LocalDate.parse("2023-02-10"),
    startTime = "08:00",
    endTime = "11:00",
    cancelled = true,
  )

fun upsertAllocation() = UpsertAllocationRequest(
  bookingId = 456L,
  startDate = LocalDate.parse("2023-01-20"),
  endDate = LocalDate.parse("2023-01-21"),
  payBandCode = "PAY",
  programStatusCode = "ALLOC",
)

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

private fun newAppointment() = CreateAppointmentRequest(
  eventDate = LocalDate.now(),
  internalLocationId = 703000,
  bookingId = 456,
  startTime = LocalTime.parse("09:00"),
  endTime = LocalTime.parse("10:00"),
  eventSubType = "APPT",
)

private fun newSentencingAdjustment(
  adjustmentTypeCode: String = "RX",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFromDate: LocalDate? = null,
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment",
) = CreateSentencingAdjustmentRequest(
  adjustmentTypeCode = adjustmentTypeCode,
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
)

private fun updateSentencingAdjustment(
  adjustmentTypeCode: String = "RX",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFromDate: LocalDate? = null,
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment",
  sentenceSequence: Int? = null,
) = UpdateSentencingAdjustmentRequest(
  adjustmentTypeCode = adjustmentTypeCode,
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
  sentenceSequence = sentenceSequence,
)

private fun newAdjudication() = CreateAdjudicationRequest(
  incident = IncidentToCreate(
    reportingStaffUsername = " JANE.BROOKES ",
    incidentDate = LocalDate.parse("2023-07-25"),
    incidentTime = "12:00:00",
    reportedDate = LocalDate.parse("2023-07-25"),
    reportedTime = "12:00:00",
    internalLocationId = 123456,
    details = "The details of the incident are as follows",
    prisonId = "MDI",
    prisonerVictimsOffenderNumbers = emptyList(),
    staffWitnessesUsernames = emptyList(),
    repairs = emptyList(),
    staffVictimsUsernames = emptyList(),
  ),
  charges = listOf(
    ChargeToCreate(
      offenceCode = "51:1",
    ),
  ),
  evidence = emptyList(),
)

private fun newLocation() = CreateLocationRequest(
  description = "MDI-FAIT",
  locationType = CreateLocationRequest.LocationType.FAIT,
  locationCode = "FAIT",
  parentLocationId = 123456,
  capacity = 10,
  userDescription = "Appointment Room 1",
  prisonId = "MDI",
)
