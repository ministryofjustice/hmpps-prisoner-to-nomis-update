package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateIncentiveResponseDto
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
    IncentivesService(incentiveApiService, nomisApiService, mappingService, updateQueueService, telemetryClient)

  @Nested
  inner class CreateIncentive {

    @Test
    fun `should log a processed visit booked event`() {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())
      whenever(nomisApiService.createIncentive(any(), any())).thenReturn(
        CreateIncentiveResponseDto(
          bookingId = 123,
          sequence = 1,
        )
      )

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123))
      )

      verify(telemetryClient).trackEvent(
        eq("incentive-created-event"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["id"]).isEqualTo("456")
          assertThat(it["iepDate"]).isEqualTo("2023-09-08")
          assertThat(it["iepTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }

    @Test
    internal fun `should not update NOMIS if incentive was created in NOMIS`() {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(
          IncentivesService.AdditionalInformation(
            id = 123,
            reason = "USER_CREATED_NOMIS"
          )
        )
      )

      verifyNoInteractions(nomisApiService)
    }

    @Test
    internal fun `should not update NOMIS if incentive already mapped (exists in nomis)`() {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())
      whenever(mappingService.getMappingGivenIncentiveId(123)).thenReturn(
        IncentiveMappingDto(
          nomisBookingId = 123,
          nomisIncentiveSequence = 1,
          incentiveId = 12345,
          mappingType = "A_TYPE"
        )
      )

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123))
      )

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a creation failure`() {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())
      whenever(nomisApiService.createIncentive(any(), any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy {
        incentivesService.createIncentive(
          IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123))
        )
      }.isInstanceOf(RuntimeException::class.java)

      verify(telemetryClient).trackEvent(
        eq("incentive-create-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["id"]).isEqualTo("456")
          assertThat(it["iepDate"]).isEqualTo("2023-09-08")
          assertThat(it["iepTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }

    @Test
    fun `should log a mapping creation failure`() {
      whenever(incentiveApiService.getIncentive(123)).thenReturn(newIncentive())
      whenever(nomisApiService.createIncentive(any(), any())).thenReturn(
        CreateIncentiveResponseDto(
          bookingId = 123,
          sequence = 1,
        )
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      incentivesService.createIncentive(
        IncentivesService.IncentiveCreatedEvent(IncentivesService.AdditionalInformation(id = 123))
      )

      verify(telemetryClient).trackEvent(
        eq("incentive-create-map-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["id"]).isEqualTo("456")
          assertThat(it["iepDate"]).isEqualTo("2023-09-08")
          assertThat(it["iepTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class RetryIncentive {

    @Test
    fun `should call mapping service`() {
      incentivesService.createIncentiveRetry(
        IncentiveContext(
          nomisBookingId = 456,
          nomisIncentiveSequence = 1,
          incentiveId = 1234
        )
      )

      verify(mappingService).createMapping(
        IncentiveMappingDto(
          456, 1, incentiveId = 1234, mappingType = "INCENTIVE_CREATED"
        )
      )
    }
  }
}

fun newIncentive(
  offenderNo: String = "AB123D",
): IepDetail = IepDetail(
  id = 456,
  prisonerNumber = offenderNo,
  agencyId = "MDI",
  iepDate = LocalDate.parse("2023-09-08"),
  iepTime = LocalDateTime.parse("2023-09-08T09:30"),
  bookingId = 123,
  sequence = 1,
  iepCode = "STD",
  iepLevel = "Standard",
  userId = "me",
)
