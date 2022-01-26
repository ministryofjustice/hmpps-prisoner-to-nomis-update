package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

internal class PrisonVisitsServiceTest {

  private val visitApiService: VisitsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val prisonVisitsService = PrisonVisitsService(visitApiService, nomisApiService, telemetryClient)

  @Nested
  inner class CreateVisit {
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitCreate("AB123D")
    }

    @Test
    fun `should log a processed visit booked event`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
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
    @BeforeEach
    internal fun setUp() {
      NomisApiExtension.nomisApi.stubVisitCancel("AB123D")
    }

    @Test
    fun `should log a processed visit cancelled event`() {
      whenever(visitApiService.getVisit("123")).thenReturn(newVisit())
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
