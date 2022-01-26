package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService
import java.time.LocalDate

internal class PrisonerDomainEventsListenerTest {
  private val prisonVisitsService: PrisonVisitsService = mock()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val listener = PrisonerDomainEventsListener(prisonVisitsService, objectMapper, eventFeatureSwitch, telemetryClient)

  @Nested
  inner class Visits {
    @Nested
    inner class WhenEnabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any())).thenReturn(true)
      }

      @Test
      internal fun `will call service with create visit data`() {
        listener.onPrisonerChange(
          message = prisonVisitCreatedMessage(
            visitId = "99",
            occurredAt = "2021-03-08T11:23:56.031Z"
          )
        )

        verify(prisonVisitsService).createVisit(
          check {
            assertThat(it.visitId).isEqualTo("99")
            assertThat(it.bookingDate).isEqualTo(LocalDate.parse("2021-03-08"))
          }
        )

        verify(telemetryClient).trackEvent(
          eq("prisoner-domain-event-received"),
          org.mockito.kotlin.check {
            assertThat(it["offenderNo"]).isEqualTo("AB12345")
            assertThat(it["eventType"]).isEqualTo("prison-visit.booked")
          },
          isNull()
        )
      }
    }

    @Nested
    inner class WhenDisabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any())).thenReturn(false)
      }

      @Test
      internal fun `will not call service`() {
        listener.onPrisonerChange(
          message = prisonVisitCreatedMessage(
            visitId = "99",
            occurredAt = "2021-03-08T11:23:56.031Z"
          )
        )

        verifyNoInteractions(prisonVisitsService)
      }
    }
  }
}

private fun objectMapper(): ObjectMapper {
  return ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .registerModule(JavaTimeModule())
    .registerModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
