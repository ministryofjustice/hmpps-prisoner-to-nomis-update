package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

internal class IncentivesDomainEventsListenerTest {
  private val incentivesService: IncentivesService = mock()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val listener =
    IncentivesDomainEventListener(
      incentivesService,
      objectMapper,
      eventFeatureSwitch,
      telemetryClient
    )

  @Nested
  inner class Incentives {
    @Nested
    inner class WhenEnabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any())).thenReturn(true)
      }

      @Test
      internal fun `will call service with create incentive data`() {
        listener.onPrisonerChange(
          message = incentiveCreatedMessage(123L)
        )

        verify(incentivesService).createIncentive(
          check {
            assertThat(it.additionalInformation.id).isEqualTo(123L)
          }
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
          message = incentiveCreatedMessage(123L)
        )

        verifyNoInteractions(incentivesService)
      }
    }

    @Nested
    inner class Retries {
      @Test
      internal fun `will call retry service with visit context data`() {
        listener.onPrisonerChange(message = incentiveRetryMessage())

        verify(incentivesService).createIncentiveRetry(
          check {
            assertThat(it.incentiveId).isEqualTo(15)
            assertThat(it.nomisBookingId).isEqualTo(12345)
            assertThat(it.nomisIncentiveSequence).isEqualTo(2)
          }
        )

        verify(telemetryClient).trackEvent(
          eq("incentive-retry-received"),
          check {
            assertThat(it["id"]).isEqualTo("15")
          },
          isNull()
        )
      }
    }
  }
}
