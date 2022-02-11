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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import javax.validation.ValidationException

internal class PrisonVisitsServiceTest {

  private val visitApiService: VisitsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: MappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val prisonVisitsService =
    PrisonVisitsService(visitApiService, nomisApiService, mappingService, telemetryClient)

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
  inner class CancelVisit {

    @Test
    fun `should log a processed visit cancelled event`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(newMapping())

      prisonVisitsService.cancelVisit(
        VisitCancelledEvent(
          additionalInformation = VisitCancelledEvent.VisitInformation(visitId = "123"),
          prisonerId = "AB123D",
          occurredAt = OffsetDateTime.now()
        )
      )

      verify(telemetryClient).trackEvent(
        eq("visit-cancelled-event"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
        },
        isNull()
      )
    }

    @Test
    fun `should log a mapping lookup failure`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
      whenever(mappingService.getMappingGivenVsipId("123")).thenReturn(null)

      assertThatThrownBy {
        prisonVisitsService.cancelVisit(
          VisitCancelledEvent(
            additionalInformation = VisitCancelledEvent.VisitInformation(visitId = "123"),
            prisonerId = "AB123D",
            occurredAt = OffsetDateTime.now()
          )
        )
      }.isInstanceOf(ValidationException::class.java)

      verify(telemetryClient).trackEvent(
        eq("visit-cancelled-failed"),
        org.mockito.kotlin.check {
          assertThat(it["offenderNo"]).isEqualTo("AB123D")
          assertThat(it["visitId"]).isEqualTo("123")
        },
        isNull()
      )
    }
  }
}

fun newVisit(offenderNo: String = "AB123D"): VisitDto = VisitDto(
  prisonerId = offenderNo,
  prisonId = "MDI",
  visitDate = LocalDate.parse("2023-09-08"),
  startTime = LocalTime.parse("08:30"),
  endTime = LocalTime.parse("09:30"),
  visitType = "SCON",
  // visitRoom = "1",
  currentStatus = "BOOKED",
  visitId = "123"
)

fun newMapping() = MappingDto(nomisId = "456", vsipId = "123", mappingType = "ONLINE")
