@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AppointmentIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ChargeToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourseScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateKeyDateAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest.OpenClosedStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest.VisitType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.EvidenceToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ExistingHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.HearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MergeDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RepairToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UnquashHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateEvidenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateKeyDateAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateSentenceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@SpringAPIServiceTest
@Import(NomisApiService::class, RetryApiService::class)
internal class NomisApiServiceTest {

  @Autowired
  private lateinit var nomisApiService: NomisApiService

  @Nested
  inner class IsAgencySwitchOnForPrisoner {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubCheckAgencySwitchForPrisoner()

      nomisApiService.isAgencySwitchOnForPrisoner(
        serviceCode = "VISIT_ALLOCATION",
        prisonNumber = "A1234BC",
      )

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the service endpoint`() = runTest {
      nomisApi.stubCheckAgencySwitchForPrisoner()

      nomisApiService.isAgencySwitchOnForPrisoner(
        serviceCode = "VISIT_ALLOCATION",
        prisonNumber = "A1234BC",
      )

      nomisApi.verify(getRequestedFor(urlPathEqualTo("/agency-switches/VISIT_ALLOCATION/prisoner/A1234BC")))
    }

    @Test
    fun `will return true when service is on for prisoner's agency`() = runTest {
      nomisApi.stubCheckAgencySwitchForPrisoner()

      assertThat(
        nomisApiService.isAgencySwitchOnForPrisoner(
          serviceCode = "VISIT_ALLOCATION",
          prisonNumber = "A1234BC",
        ),
      ).isTrue
    }

    @Test
    fun `will return false if exception thrown when service not on for prisoner's agency`() = runTest {
      nomisApi.stubCheckAgencySwitchForPrisonerNotFound()
      assertThat(
        nomisApiService.isAgencySwitchOnForPrisoner(
          serviceCode = "VISIT_ALLOCATION",
          prisonNumber = "A1234BC",
        ),
      ).isFalse
    }
  }

  @Nested
  inner class IsAgencySwitchOnForAgency {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgency()

      nomisApiService.isAgencySwitchOnForAgency(
        serviceCode = "VISIT_ALLOCATION",
        agencyId = "MDI",
      )

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the service endpoint`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgency()

      nomisApiService.isAgencySwitchOnForAgency(
        serviceCode = "VISIT_ALLOCATION",
        agencyId = "MDI",
      )

      nomisApi.verify(getRequestedFor(urlPathEqualTo("/agency-switches/VISIT_ALLOCATION/agency/MDI")))
    }

    @Test
    fun `will return true when service is on for agency`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgency()

      assertThat(
        nomisApiService.isAgencySwitchOnForAgency(
          serviceCode = "VISIT_ALLOCATION",
          agencyId = "MDI",
        ),
      ).isTrue
    }

    @Test
    fun `will return false if exception thrown when service not on for agency`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgencyNotFound()
      assertThat(
        nomisApiService.isAgencySwitchOnForAgency(
          serviceCode = "VISIT_ALLOCATION",
          agencyId = "MDI",
        ),
      ).isFalse
    }
  }

  @Nested
  inner class GetActivePrisons {

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetActivePrisons()

      nomisApiService.getActivePrisons()

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the service endpoint`() = runTest {
      nomisApi.stubGetActivePrisons()

      nomisApiService.getActivePrisons()

      nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisons")))
    }

    @Test
    fun `will return prison data`() = runTest {
      nomisApi.stubGetActivePrisons()

      val prisons = nomisApiService.getActivePrisons()
      assertThat(prisons[0].id).isEqualTo("ASI")
      assertThat(prisons[0].description).isEqualTo("Ashfield")
      assertThat(prisons[1].id).isEqualTo("LEI")
      assertThat(prisons[1].description).isEqualTo("Leeds")
      assertThat(prisons[2].id).isEqualTo("MDI")
      assertThat(prisons[2].description).isEqualTo("Moorland")
    }
  }

  @Nested
  inner class CreateVisit {

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit("AB123D", newVisit())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post visit data to nomis api`() = runTest {
      nomisApi.stubVisitCreate("AB123D")

      nomisApiService.createVisit("AB123D", newVisit())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/AB123D/visits"))
          .withRequestBody(matchingJsonPath("$.prisonId", equalTo("MDI"))),
      )
    }

    @Test
    fun `when offender is not found an exception is thrown`() = runTest {
      nomisApi.stubVisitCreateWithError("AB123D", 404)

      assertThrows<NotFound> {
        nomisApiService.createVisit("AB123D", newVisit())
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubVisitCreateWithError("AB123D", 503)

      assertThrows<ServiceUnavailable> {
        nomisApiService.createVisit("AB123D", newVisit())
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
  inner class CreateAppointment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubAppointmentCreate("""{ "id": 12345, "eventId": 1234 }""")

      nomisApiService.createAppointment(newAppointment())

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/appointments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to nomis api`(): Unit = runTest {
      nomisApi.stubAppointmentCreate("""{ "id": 12345, "eventId": 1234 }""")

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
  inner class GetAppointmentIds {

    @BeforeEach
    internal fun setUp() {
      nomisApi.stubGetAppointmentIds()
    }

    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApiService.getAppointmentIds(
        prisonIds = listOf("BMI", "SWI"),
        fromDate = LocalDate.of(2025, 2, 3),
        toDate = LocalDate.of(2025, 3, 4),
        pageNumber = 4,
        pageSize = 10,
      )

      nomisApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass parameters to API`() = runTest {
      assertThat(
        nomisApiService.getAppointmentIds(
          prisonIds = listOf("BMI", "SWI"),
          fromDate = LocalDate.of(2025, 2, 3),
          toDate = LocalDate.of(2025, 3, 4),
          pageNumber = 0,
          pageSize = 10,
        ),
      ).isEqualTo(
        PageImpl(
          listOf(AppointmentIdResponse(123456789)),
          Pageable.ofSize(10),
          41,
        ),
      )

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/appointments/ids"))
          .withQueryParam("prisonIds", havingExactly("BMI", "SWI"))
          .withQueryParam("fromDate", equalTo("2025-02-03"))
          .withQueryParam("toDate", equalTo("2025-03-04"))
          .withQueryParam("page", equalTo("0"))
          .withQueryParam("size", equalTo("10")),
      )
    }
  }

  @Nested
  inner class GetSentencingAdjustments {
    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetSentencingAdjustments(123456)

      nomisApiService.getAdjustments(123456)

      nomisApi.verify(
        getRequestedFor(urlEqualTo("/prisoners/booking-id/123456/sentencing-adjustments?active-only=true"))
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

      nomisApiService.createKeyDateAdjustment(12345, newKeyDateAdjustment())

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
        request = newKeyDateAdjustment(
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
        nomisApiService.createKeyDateAdjustment(12345, newKeyDateAdjustment())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentCreateWithError(
        bookingId = 12345,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.createKeyDateAdjustment(12345, newKeyDateAdjustment())
      }
    }
  }

  @Nested
  inner class UpdateKeyDateAdjustment {

    @Test
    fun `should call nomis api with OAuth2 token`(): Unit = runTest {
      nomisApi.stubKeyDateAdjustmentUpdate(adjustmentId = 98765)

      nomisApiService.updateKeyDateAdjustment(98765, updateKeyDateAdjustmentRequest())

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
        request = updateKeyDateAdjustmentRequest(
          adjustmentTypeCode = "LAL",
          adjustmentDate = LocalDate.parse("2022-01-01"),
          adjustmentDays = 9,
          adjustmentFromDate = LocalDate.parse("2020-07-19"),
          comment = "Adjusted for remand",
        ),
      )

      nomisApi.verify(
        putRequestedFor(urlEqualTo("/key-date-adjustments/98765"))
          .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("LAL")))
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
        nomisApiService.updateKeyDateAdjustment(98765, updateKeyDateAdjustmentRequest())
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      nomisApi.stubKeyDateAdjustmentUpdateWithError(
        adjustmentId = 98765,
        status = 503,
      )

      assertThrows<ServiceUnavailable> {
        nomisApiService.updateKeyDateAdjustment(98765, updateKeyDateAdjustmentRequest())
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
              award = HearingResultAwardRequest(
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

  @Nested
  inner class GetMergesSinceDate {
    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stubGetMergesFromDate(offenderNo = "A1234AA")

      nomisApiService.mergesSinceDate(offenderNo = "A1234AA", fromDate = LocalDate.now())

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/merges"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass date to service`() = runTest {
      nomisApi.stubGetMergesFromDate(offenderNo = "A1234AA")

      nomisApiService.mergesSinceDate(offenderNo = "A1234AA", fromDate = LocalDate.parse("2022-01-01"))

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/merges"))
          .withQueryParam("fromDate", equalTo("2022-01-01")),
      )
    }

    @Test
    fun `can return multiple merges`() = runTest {
      nomisApi.stubGetMergesFromDate(
        offenderNo = "A1234AA",
        merges = listOf(
          MergeDetail(deletedOffenderNo = "A1234AB", retainedOffenderNo = "A1234AA", previousBookingId = 1234, activeBookingId = 1235, requestDateTime = LocalDateTime.parse("2024-02-02T12:34:56")),
          MergeDetail(deletedOffenderNo = "A1234AC", retainedOffenderNo = "A1234AA", previousBookingId = 1233, activeBookingId = 1234, requestDateTime = LocalDateTime.parse("2024-02-01T13:34:56")),
        ),
      )

      val merges = nomisApiService.mergesSinceDate(offenderNo = "A1234AA", fromDate = LocalDate.parse("2022-01-01"))
      assertThat(merges).hasSize(2)
      assertThat(merges.first().requestDateTime).isEqualTo("2024-02-02T12:34:56")
      assertThat(merges.last().requestDateTime).isEqualTo("2024-02-01T13:34:56")
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

  @Nested
  inner class GetAllLatestBookings {
    @Test
    fun `should call nomis api with OAuth2 token`() = runTest {
      nomisApi.stuGetAllLatestBookings()

      nomisApiService.getAllLatestBookings(activeOnly = true, lastBookingId = 0, pageSize = 10)

      nomisApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass parameters to API`() = runTest {
      nomisApi.stuGetAllLatestBookings()

      nomisApiService.getAllLatestBookings(activeOnly = true, lastBookingId = 54321, pageSize = 1000)

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", equalTo("54321"))
          .withQueryParam("activeOnly", equalTo("true"))
          .withQueryParam("pageSize", equalTo("1000")),
      )
    }
  }
}

fun newVisit(): CreateVisitRequest = CreateVisitRequest(
  prisonId = "MDI",
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT.toString(),
  visitorPersonIds = listOf(),
  visitType = VisitType.SCON,
  issueDate = LocalDate.now(),
  visitComment = "VSIP ref: 123",
  visitOrderComment = "VO VSIP ref: 123",
  room = "Main visits room",
  openClosedStatus = OpenClosedStatus.CLOSED,
)

fun cancelVisit(): CancelVisitDto = CancelVisitDto(offenderNo = "AB123D", nomisVisitId = "12", outcome = "VISCANC")

fun updateVisit(): UpdateVisitDto = UpdateVisitDto(
  startDateTime = LocalDateTime.now(),
  endTime = LocalTime.MIDNIGHT,
  visitorPersonIds = listOf(),
  room = "Main visits room",
  openClosedStatus = "CLOSED",
  visitComment = "visit comment",
)

fun newIncentive() = CreateIncentiveRequest(
  iepDateTime = LocalDateTime.now(),
  prisonId = "MDI",
  iepLevel = "High",
  userId = "BILLYBOB",
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

fun updateScheduledInstance() = CourseScheduleRequest(
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
) = CreateSentenceAdjustmentRequest(
  adjustmentTypeCode = CreateSentenceAdjustmentRequest.AdjustmentTypeCode.valueOf(adjustmentTypeCode),
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
)
private fun newKeyDateAdjustment(
  adjustmentTypeCode: String = "LAL",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFromDate: LocalDate? = LocalDate.now(),
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment",
) = CreateKeyDateAdjustmentRequest(
  adjustmentTypeCode = CreateKeyDateAdjustmentRequest.AdjustmentTypeCode.valueOf(adjustmentTypeCode),
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate!!,
  adjustmentDays = adjustmentDays,
  comment = comment,
)

private fun updateSentencingAdjustment(
  adjustmentTypeCode: String = "RX",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFromDate: LocalDate? = null,
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment",
  sentenceSequence: Int = 1,
) = UpdateSentenceAdjustmentRequest(
  adjustmentTypeCode = UpdateSentenceAdjustmentRequest.AdjustmentTypeCode.valueOf(adjustmentTypeCode),
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
  sentenceSequence = sentenceSequence.toLong(),
)

private fun updateKeyDateAdjustmentRequest(
  adjustmentTypeCode: String = "LAL",
  adjustmentDate: LocalDate = LocalDate.now(),
  adjustmentFromDate: LocalDate? = LocalDate.now(),
  adjustmentDays: Long = 99,
  comment: String? = "Adjustment comment",
) = UpdateKeyDateAdjustmentRequest(
  adjustmentTypeCode = UpdateKeyDateAdjustmentRequest.AdjustmentTypeCode.valueOf(adjustmentTypeCode),
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate!!,
  adjustmentDays = adjustmentDays,
  comment = comment,
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
