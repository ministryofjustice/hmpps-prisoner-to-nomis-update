@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
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
  private val incentivesReferenceService: IncentivesReferenceService = mock()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()

  private val listener =
    IncentivesDomainEventListener(
      incentivesService,
      incentivesReferenceService,
      objectMapper,
      eventFeatureSwitch,
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
      internal fun `will call service with create incentive data`() = runTest {
        listener.onMessage(
          rawMessage = incentiveCreatedMessage(123L),
        ).join()

        verify(incentivesService).createIncentive(
          check {
            assertThat(it.additionalInformation.id).isEqualTo(123L)
          },
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
        listener.onMessage(
          rawMessage = incentiveCreatedMessage(123L),
        ).join()

        verifyNoInteractions(incentivesService)
      }
    }

    @Nested
    inner class Retries {
      @Test
      internal fun `will call retry service with visit context data`() = runTest {
        listener.onMessage(rawMessage = incentiveRetryMessage()).join()

        verify(incentivesService).retryCreateMapping(any())
      }
    }
  }
}
