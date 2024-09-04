@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitResponseDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
import java.time.OffsetDateTime

internal class VisitsServiceTest {

  private val visitApiService: VisitsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: VisitsMappingService = mock()
  private val updateQueueService: VisitsUpdateQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val visitsService =
    VisitsService(visitApiService, nomisApiService, mappingService, updateQueueService, telemetryClient, objectMapper())

  @Nested
  inner class CreateVisit {

    @Test
    fun `should log a processed visit booked event`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipIdOrNull("123")).thenReturn(null)
      whenever(nomisApiService.createVisit(any())).thenReturn(Result.success(CreateVisitResponseDto("456")))

      visitsService.createVisit(
        VisitBookedEvent(
          prisonerId = "AB123D",
          additionalInformation = VisitBookedEvent.VisitInformation("123"),
          occurredAt = OffsetDateTime.now(),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visit-create-success"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["nomisId"]).isEqualTo("456")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["startDateTime"]).isEqualTo("2023-09-08T08:30:00")
          assertThat(it["endTime"]).isEqualTo("09:30:00")
        },
        isNull(),
      )
    }

    @Test
    fun `should log an existing mapping`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipIdOrNull("123")).thenReturn(newMapping())

      visitsService.createVisit(
        VisitBookedEvent(
          prisonerId = "AB123D",
          additionalInformation = VisitBookedEvent.VisitInformation("123"),
          occurredAt = OffsetDateTime.now(),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visit-create-duplicate"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
        },
        isNull(),
      )
    }

    @Test
    fun `should log a mapping creation failure`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipIdOrNull("123")).thenReturn(null)
      whenever(nomisApiService.createVisit(any())).thenReturn(Result.success(CreateVisitResponseDto("456")))
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      visitsService.createVisit(
        VisitBookedEvent(
          prisonerId = "AB123D",
          additionalInformation = VisitBookedEvent.VisitInformation("123"),
          occurredAt = OffsetDateTime.now(),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visit-mapping-create-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["nomisId"]).isEqualTo("456")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["startDateTime"]).isEqualTo("2023-09-08T08:30:00")
          assertThat(it["endTime"]).isEqualTo("09:30:00")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class RetryVisit {

    @Test
    fun `should call mapping service`() = runTest {
      visitsService.retryCreateMapping(
        """
          { "mapping" :
            {
              "nomisId": "AB123D",
              "vsipId": "24"
            }, 
            "entityName": "visit",
            "telemetryAttributes": {}
          }
        """.trimIndent(),
      )

      verify(mappingService).createMapping(VisitMappingDto("AB123D", "24", mappingType = "ONLINE"))
    }
  }

  @Nested
  inner class CancelVisit {

    @Test
    fun `should log a processed visit cancelled event`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      visitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now(),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visit-cancelled-success"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["nomisVisitId"]).isEqualTo("456")
        },
        isNull(),
      )

      verify(visitApiService).getVisit("123")
    }

    @Test
    fun `should map a cancellation outcome correctly`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      visitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now(),
        ),
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", "HMP"))
    }

    @Test
    fun `should handle a null cancellation outcome correctly (set to default 'ADMIN')`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit(outcome = null))
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      visitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now(),
        ),
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", "ADMIN"))
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "ADMINISTRATIVE_CANCELLATION,ADMIN",
        "ADMINISTRATIVE_ERROR,ADMIN",
        "ESTABLISHMENT_CANCELLED,HMP",
        "VISITOR_FAILED_SECURITY_CHECKS,NO_ID",
        "NO_VISITING_ORDER,NO_VO",
        "VISITOR_DID_NOT_ARRIVE,NSHOW",
        "PRISONER_CANCELLED,OFFCANC",
        "PRISONER_REFUSED_TO_ATTEND,REFUSED",
        "VISITOR_CANCELLED,VISCANC",
        "VISIT_ORDER_CANCELLED,VO_CANCEL",
        "BATCH_CANCELLATION,ADMIN",
        "PRISONER_REFUSED_TO_ATTEND,REFUSED",
      ],
    )
    fun `should map all cancellation outcome correctly`(vsipOutcome: String, nomisOutcome: String) = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit(outcome = vsipOutcome))
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      visitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now(),
        ),
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", nomisOutcome))
    }

    @Test
    fun `should handle an unexpected cancellation outcome correctly (set to default 'ADMIN')`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit(outcome = "HMMMMMMM"))
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      visitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now(),
        ),
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", "ADMIN"))
    }

    @Test
    fun `should log a mapping lookup failure`() = runTest {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenThrow(RuntimeException("BOOM"))

      assertThrows<RuntimeException> {
        visitsService.cancelVisit(
          VisitCancelledEvent(
            additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
            prisonerId = "AB123D",
            occurredAt = OffsetDateTime.now(),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("visit-cancelled-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
        },
        isNull(),
      )
    }
  }
}

fun newVisit(
  offenderNo: String = "AB123D",
  outcome: String? = VsipOutcomeStatus.ESTABLISHMENT_CANCELLED.name,
): VisitDto = VisitDto(
  prisonerId = offenderNo,
  prisonId = "MDI",
  startTimestamp = LocalDateTime.parse("2023-09-08T08:30"),
  endTimestamp = LocalDateTime.parse("2023-09-08T09:30"),
  visitType = "SOCIAL",
  visitStatus = "BOOKED",
  reference = "123",
  outcomeStatus = outcome,
  visitRoom = "Main visit room",
  visitRestriction = "OPEN",
)

fun newMapping() = VisitMappingDto(nomisId = "456", vsipId = "123", mappingType = "ONLINE")
