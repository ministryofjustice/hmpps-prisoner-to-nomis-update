package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

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
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateQueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.VisitContext
import java.time.LocalDateTime
import java.time.OffsetDateTime
import javax.validation.ValidationException

internal class PrisonVisitsServiceTest {

  private val visitApiService: VisitsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: MappingService = mock()
  private val updateQueueService: UpdateQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val prisonVisitsService =
    PrisonVisitsService(visitApiService, nomisApiService, mappingService, updateQueueService, telemetryClient)

  @Nested
  inner class CreateVisit {

    @Test
    fun `should log a processed visit booked event`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(null)
      whenever(nomisApiService.createVisit(any())).thenReturn("456")

      prisonVisitsService.createVisit(
        VisitBookedEvent(
          prisonerId = "AB123D",
          additionalInformation = VisitBookedEvent.VisitInformation("123"), occurredAt = OffsetDateTime.now()
        )
      )

      verify(telemetryClient).trackEvent(
        eq("visit-booked-event"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["nomisVisitId"]).isEqualTo("456")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["startDateTime"]).isEqualTo("2023-09-08T08:30:00")
          assertThat(it["endTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }

    @Test
    fun `should log an existing mapping`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      prisonVisitsService.createVisit(
        VisitBookedEvent(
          prisonerId = "AB123D",
          additionalInformation = VisitBookedEvent.VisitInformation("123"), occurredAt = OffsetDateTime.now()
        )
      )

      verify(telemetryClient).trackEvent(
        eq("visit-booked-get-map-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["startDateTime"]).isEqualTo("2023-09-08T08:30:00")
          assertThat(it["endTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }

    @Test
    fun `should log a creation failure`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(null)
      whenever(nomisApiService.createVisit(any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy {
        prisonVisitsService.createVisit(
          VisitBookedEvent(
            prisonerId = "AB123D",
            additionalInformation = VisitBookedEvent.VisitInformation("123"), occurredAt = OffsetDateTime.now()
          )
        )
      }.isInstanceOf(RuntimeException::class.java)

      verify(telemetryClient).trackEvent(
        eq("visit-booked-create-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["startDateTime"]).isEqualTo("2023-09-08T08:30:00")
          assertThat(it["endTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }

    @Test
    fun `should log a mapping creation failure`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(null)
      whenever(nomisApiService.createVisit(any())).thenReturn("456")
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      prisonVisitsService.createVisit(
        VisitBookedEvent(
          prisonerId = "AB123D",
          additionalInformation = VisitBookedEvent.VisitInformation("123"), occurredAt = OffsetDateTime.now()
        )
      )

      verify(telemetryClient).trackEvent(
        eq("visit-booked-create-map-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["nomisVisitId"]).isEqualTo("456")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["startDateTime"]).isEqualTo("2023-09-08T08:30:00")
          assertThat(it["endTime"]).isEqualTo("09:30:00")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class RetryVisit {

    @Test
    fun `should call mapping service`() {

      prisonVisitsService.createVisitRetry(
        VisitContext(nomisId = "AB123D", vsipId = "24")
      )

      verify(mappingService).createMapping(MappingDto("AB123D", "24", mappingType = "ONLINE"))
    }
  }

  @Nested
  inner class CancelVisit {

    @Test
    fun `should log a processed visit cancelled event`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      prisonVisitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now()
        )
      )

      verify(telemetryClient).trackEvent(
        eq("visit-cancelled-event"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
          assertThat(it["nomisVisitId"]).isEqualTo("456")
        },
        isNull()
      )

      verify(visitApiService).getVisit("123")
    }

    @Test
    fun `should map a cancellation outcome correctly`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      prisonVisitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now()
        )
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", "HMP"))
    }

    @Test
    fun `should handle a null cancellation outcome correctly (set to default 'ADMIN')`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit(outcome = null))
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      prisonVisitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now()
        )
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", "ADMIN"))
    }

    @Test
    fun `should handle an unexpected cancellation outcome correctly (set to default 'ADMIN')`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit(outcome = "HMMMMMMM"))
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      prisonVisitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now()
        )
      )
      verify(nomisApiService).cancelVisit(CancelVisitDto("AB123D", "456", "ADMIN"))
    }

    @Test
    fun `should log a mapping lookup failure`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(null)

      assertThatThrownBy {
        prisonVisitsService.cancelVisit(
          VisitCancelledEvent(
            additionalInformation = VisitCancelledEvent.VisitInformation(reference = "123"),
            prisonerId = "AB123D",
            occurredAt = OffsetDateTime.now()
          )
        )
      }.isInstanceOf(ValidationException::class.java)

      verify(telemetryClient).trackEvent(
        eq("visit-cancelled-mapping-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
        },
        isNull()
      )
    }
  }
}

fun newVisit(offenderNo: String = "AB123D", outcome: String? = VsipOutcomeStatus.ESTABLISHMENT_CANCELLED.name): VisitDto = VisitDto(
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

fun newMapping() = MappingDto(nomisId = "456", vsipId = "123", mappingType = "ONLINE")
