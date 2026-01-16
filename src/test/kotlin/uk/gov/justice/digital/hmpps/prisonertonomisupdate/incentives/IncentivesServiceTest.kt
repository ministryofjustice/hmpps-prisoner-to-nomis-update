@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncentiveMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncentiveMappingDto.MappingType.INCENTIVE_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncentiveMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateIncentiveResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

internal class IncentivesServiceTest {

  private val incentiveApiService: IncentivesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: IncentivesMappingService = mock()
  private val updateQueueService: IncentivesUpdateQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val incentivesService =
    IncentivesService(incentiveApiService, nomisApiService, mappingService, updateQueueService, telemetryClient, objectMapper())

  @Nested
  inner class CreateIncentive {

    @Test
    fun `should log a processed visit booked event`() = runTest {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive(id = 123))
      whenever(nomisApiService.createIncentive(any(), any())).thenReturn(
        CreateIncentiveResponse(
          bookingId = 123,
          sequence = 1,
        ),
      )

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123, nomsNumber = "AB123D")),
      )

      verify(telemetryClient).trackEvent(
        eq("incentive-create-success"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["id"]).isEqualTo("123")
          assertThat(it["iepDate"]).isEqualTo("2023-09-08")
          assertThat(it["iepTime"]).isEqualTo("09:30:00")
        },
        isNull(),
      )
    }

    @Test
    internal fun `should not update NOMIS if incentive was created in NOMIS`() = runTest {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(
          IncentivesService.AdditionalInformation(
            id = 123,
            reason = "USER_CREATED_NOMIS",
          ),
        ),
      )

      verifyNoInteractions(nomisApiService)
    }

    @Test
    internal fun `should not update NOMIS if incentive already mapped (exists in nomis)`() = runTest {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())
      whenever(mappingService.getMappingGivenIncentiveIdOrNull(123)).thenReturn(
        IncentiveMappingDto(
          nomisBookingId = 123,
          nomisIncentiveSequence = 1,
          incentiveId = 12345,
          mappingType = NOMIS_CREATED,
        ),
      )

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123)),
      )

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a mapping creation failure`() = runTest {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive(id = 123))
      whenever(nomisApiService.createIncentive(any(), any())).thenReturn(
        CreateIncentiveResponse(
          bookingId = 123,
          sequence = 1,
        ),
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123, nomsNumber = "AB123D")),
      )

      verify(telemetryClient).trackEvent(
        eq("incentive-mapping-create-failed"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["id"]).isEqualTo("123")
          assertThat(it["iepDate"]).isEqualTo("2023-09-08")
          assertThat(it["iepTime"]).isEqualTo("09:30:00")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class RetryIncentive {

    @Test
    fun `should call mapping service`() = runTest {
      incentivesService.retryCreateMapping(
        """
          { 
            "mapping" : {
              "nomisBookingId": 456,
              "nomisIncentiveSequence": 1,
              "incentiveId": 1234
            }, 
            "entityName": "incentive",
            "telemetryAttributes": {}
          }
        """.trimIndent(),
      )

      verify(mappingService).createMapping(
        IncentiveMappingDto(
          456,
          1,
          incentiveId = 1234,
          mappingType = INCENTIVE_CREATED,
        ),
      )
    }
  }
}

fun newIncentive(
  offenderNo: String = "AB123D",
  id: Long = 456,
): IncentiveReviewDetail = IncentiveReviewDetail(
  id = id,
  prisonerNumber = offenderNo,
  agencyId = "MDI",
  iepDate = LocalDate.parse("2023-09-08"),
  iepTime = LocalDateTime.parse("2023-09-08T09:30"),
  bookingId = 123,
  iepCode = "STD",
  iepLevel = "Standard",
  userId = "me",
  reviewType = IncentiveReviewDetail.ReviewType.REVIEW,
  auditModuleName = "audit",
  isRealReview = true,
)
