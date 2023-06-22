@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourseScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.GetAttendanceStatusRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
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
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post visit data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit(newVisit(offenderNo = "AB123D"))

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withRequestBody(matchingJsonPath("$.offenderNo", equalTo("AB123D"))),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubVisitCreateWithError("AB123D", 404)

      assertThrows<NotFound> {
        nomisApiService.createVisit(newVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubVisitCreateWithError("AB123D", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createVisit(newVisit())
      }
    }
  }

  @Nested
  inner class CancelVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitCancel(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApiService.cancelVisit(cancelVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post cancel request to nomis api`() = runTest {
      nomisApiService.cancelVisit(cancelVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12/cancel")),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubVisitCancelWithError("AB123D", "12", 404)

      assertThrows<NotFound> {
        nomisApiService.cancelVisit(cancelVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubVisitCancelWithError("AB123D", "12", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.cancelVisit(cancelVisit())
      }
    }
  }

  @Nested
  inner class UpdateVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitUpdate(prisonerId = "AB123D", visitId = "12")
    }

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post cancel request to nomis api`() = runTest {
      nomisApiService.updateVisit("AB123D", "12", updateVisit())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/prisoners/AB123D/visits/12")),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubVisitUpdateWithError("AB123D", "12", 404)

      assertThrows<NotFound> {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubVisitUpdateWithError("AB123D", "12", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateVisit("AB123D", "12", updateVisit())
      }
    }
  }

  @Nested
  inner class CreateIncentive {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubIncentiveCreate(456)

      nomisApiService.createIncentive(456, newIncentive())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/456/incentives"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("MDI"))),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubIncentiveCreateWithError(456, 404)

      assertThrows<NotFound> {
        nomisApiService.createIncentive(456, newIncentive())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubIncentiveCreateWithError(456, 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createIncentive(456, newIncentive())
      }
    }
  }

  @Nested
  inner class GetCurrentIncentive {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubCurrentIncentiveGet(99, "STD")
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      nomisApiService.getCurrentIncentive(99)

      NomisApiExtension.nomisApi.verify(
        WireMock.getRequestedFor(urlEqualTo("/incentives/booking-id/99/current"))
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
      NomisApiExtension.nomisApi.stubCurrentIncentiveGetWithError(99, responseCode = 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.getCurrentIncentive(99)
      }
    }
  }

  @Nested
  inner class CreateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubActivityCreate("""{ "courseActivityId": 456, "courseSchedules": [] }""")

      nomisApiService.createActivity(newActivity())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubActivityCreate("""{ "courseActivityId": 456, "courseSchedules": [] }""")

      nomisApiService.createActivity(newActivity())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("WWI"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubActivityCreateWithError(503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createActivity(newActivity())
      }
    }
  }

  @Nested
  inner class UpdateActivity {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubActivityUpdate(1L, """{ "courseActivityId": 1, "courseSchedules": [] }""")

      nomisApiService.updateActivity(1L, updateActivity())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubActivityUpdate(1L, """{ "courseActivityId": 1, "courseSchedules": [] }""")

      nomisApiService.updateActivity(1L, updateActivity())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1"))
          .withRequestBody(matchingJsonPath("$.startDate", equalTo("2023-02-01")))
          .withRequestBody(matchingJsonPath("$.capacity", equalTo("123")))
          .withRequestBody(matchingJsonPath("$.description", equalTo("updated activity")))
          .withRequestBody(matchingJsonPath("$.minimumIncentiveLevelCode", equalTo("STD")))
          .withRequestBody(matchingJsonPath("$.payPerSession", equalTo("F")))
          .withRequestBody(matchingJsonPath("$.excludeBankHolidays", equalTo("true")))
          .withRequestBody(matchingJsonPath("$.endDate", equalTo("2023-02-10")))
          .withRequestBody(matchingJsonPath("$.internalLocationId", equalTo("703000")))
          .withRequestBody(matchingJsonPath("$.programCode", equalTo("PROGRAM_SERVICE"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubActivityUpdateWithError(1, 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateActivity(1L, updateActivity())
      }
    }
  }

  @Nested
  inner class UpdateScheduledInstance {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubScheduledInstanceUpdate(1L, """{ "courseScheduleId": 2 }""")

      nomisApiService.updateScheduledInstance(1L, updateScheduledInstance())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1/schedule"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubScheduleInstancesUpdate(1L)

      nomisApiService.updateScheduledInstance(1L, updateScheduledInstance())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/1/schedule"))
          .withRequestBody(matchingJsonPath("date", equalTo("2023-02-10")))
          .withRequestBody(matchingJsonPath("startTime", equalTo("08:00")))
          .withRequestBody(matchingJsonPath("endTime", equalTo("11:00")))
          .withRequestBody(matchingJsonPath("cancelled", equalTo("true"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubScheduledInstanceUpdateWithError(1, 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateScheduledInstance(1L, updateScheduledInstance())
      }
    }
  }

  @Nested
  inner class UpsertAllocation {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubAllocationUpsert(12)

      nomisApiService.upsertAllocation(12, upsertAllocation())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/allocation"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will send data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubAllocationUpsert(12)

      nomisApiService.upsertAllocation(12, upsertAllocation())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/12/allocation"))
          .withRequestBody(matchingJsonPath("$.bookingId", equalTo("456"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubAllocationUpsertWithError(12, 400)

      assertThrows<BadRequest> {
        nomisApiService.upsertAllocation(12, upsertAllocation())
      }
    }
  }

  @Nested
  inner class UpsertAttendance {

    val validResponse = """{
        "eventId": 1,
        "courseScheduleId": 2,
        "created": true
      }
    """.trimMargin()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubUpsertAttendance(11, 22, validResponse)

      nomisApiService.upsertAttendance(11, 22, newAttendance())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/11/booking/22/attendance"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubUpsertAttendance(11, 22, validResponse)

      nomisApiService.upsertAttendance(11, 22, newAttendance())

      NomisApiExtension.nomisApi.verify(
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
      NomisApiExtension.nomisApi.stubUpsertAttendance(11, 22, validResponse)

      val response = nomisApiService.upsertAttendance(11, 22, newAttendance())

      with(response) {
        assertThat(eventId).isEqualTo(1)
        assertThat(courseScheduleId).isEqualTo(2)
        assertThat(created).isTrue()
      }
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubUpsertAttendanceWithError(11, 22, 400)

      assertThrows<BadRequest> {
        nomisApiService.upsertAttendance(11, 22, newAttendance())
      }
    }
  }

  @Nested
  inner class GetAttendanceStatus {

    val validResponse = """{
        "eventStatus": "COMP"
      }
    """.trimMargin()

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      NomisApiExtension.nomisApi.stubGetAttendanceStatus(11, 22, validResponse)

      nomisApiService.getAttendanceStatus(11, 22, newGetAttendanceStatusRequest())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities/11/booking/22/attendance-status"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubGetAttendanceStatus(11, 22, validResponse)

      nomisApiService.getAttendanceStatus(11, 22, newGetAttendanceStatusRequest())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities/11/booking/22/attendance-status"))
          .withRequestBody(matchingJsonPath("$.scheduleDate", equalTo("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("$.startTime", equalTo("11:00")))
          .withRequestBody(matchingJsonPath("$.endTime", equalTo("13:00"))),
      )
    }

    @Test
    fun `will parse the response`() = runTest {
      NomisApiExtension.nomisApi.stubGetAttendanceStatus(11, 22, validResponse)

      val response = nomisApiService.getAttendanceStatus(11, 22, newGetAttendanceStatusRequest())

      assertThat(response?.eventStatus).isEqualTo("COMP")
    }

    @Test
    fun `will parse an empty response`() = runTest {
      NomisApiExtension.nomisApi.stubGetAttendanceStatusWithError(11, 22, 404)

      val response = nomisApiService.getAttendanceStatus(11, 22, newGetAttendanceStatusRequest())

      assertThat(response).isNull()
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubGetAttendanceStatusWithError(11, 22, 400)

      assertThrows<BadRequest> {
        nomisApiService.getAttendanceStatus(11, 22, newGetAttendanceStatusRequest())
      }
    }
  }

  @Nested
  inner class CreateAppointment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      NomisApiExtension.nomisApi.stubAppointmentCreate("""{ "id": 12345 }""")

      nomisApiService.createAppointment(newAppointment())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/appointments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`(): Unit = runTest {
      NomisApiExtension.nomisApi.stubAppointmentCreate("""{ "id": 12345 }""")

      nomisApiService.createAppointment(newAppointment())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/appointments"))
          .withRequestBody(matchingJsonPath("$.internalLocationId", equalTo("703000"))),
      )
    }

    @Test
    fun `when any error response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubAppointmentCreateWithError()

      assertThrows<ServiceUnavailable> {
        nomisApiService.createActivity(newActivity())
      }
    }
  }

  @Nested
  inner class CreateSentenceAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreate(bookingId = 12345, sentenceSequence = 2)

      nomisApiService.createSentenceAdjustment(12345, 2, newSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/sentences/2/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post sentence adjustment data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreate(bookingId = 12345, sentenceSequence = 2)

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

      NomisApiExtension.nomisApi.verify(
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
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreateWithError(
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
      NomisApiExtension.nomisApi.stubSentenceAdjustmentCreateWithError(
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
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put sentence adjustment data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateSentenceAdjustment(
        98765,
        request = updateSentencingAdjustment(
          adjustmentTypeCode = "RX",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFromDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand",
        ),
      )

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
      )
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.updateSentenceAdjustment(98765, updateSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentUpdateWithError(
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
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteSentenceAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/sentence-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will delete sentence adjustment from nomis`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteSentenceAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/sentence-adjustments/98765")),
      )
    }

    @Test
    internal fun `if 404 - which is not expected - is returned an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.deleteSentenceAdjustment(98765)
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubSentenceAdjustmentDeleteWithError(
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
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreate(bookingId = 12345)

      nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post key date adjustment data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreate(bookingId = 12345)

      nomisApiService.createKeyDateAdjustment(
        bookingId = 12345,
        request = newSentencingAdjustment(
          adjustmentTypeCode = "ADA",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
        ),
      )

      NomisApiExtension.nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/booking-id/12345/adjustments"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("ADA")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9"))),
      )
    }

    @Test
    internal fun `when booking is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreateWithError(
        bookingId = 12345,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.createKeyDateAdjustment(12345, newSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentCreateWithError(
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
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment())

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will put key date adjustment data to nomis api`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

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

      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
          .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("9")))
          .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
          .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
      )
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.updateKeyDateAdjustment(98765, updateSentencingAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentUpdateWithError(
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
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteKeyDateAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will delete key date adjustment from nomis`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDelete(adjustmentId = 98765)

      nomisApiService.deleteKeyDateAdjustment(98765)

      NomisApiExtension.nomisApi.verify(
        deleteRequestedFor(urlEqualTo("/key-date-adjustments/98765")),
      )
    }

    @Test
    internal fun `if 404 - which is not expected - is returned an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 404,
      )

      assertThrows<NotFound> {
        nomisApiService.deleteKeyDateAdjustment(98765)
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      NomisApiExtension.nomisApi.stubKeyDateAdjustmentDeleteWithError(
        adjustmentId = 98765,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.deleteKeyDateAdjustment(98765)
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

private fun newGetAttendanceStatusRequest() = GetAttendanceStatusRequest(
  scheduleDate = LocalDate.now(),
  startTime = "11:00",
  endTime = "13:00",
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
) = UpdateSentencingAdjustmentRequest(
  adjustmentTypeCode = adjustmentTypeCode,
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
)
