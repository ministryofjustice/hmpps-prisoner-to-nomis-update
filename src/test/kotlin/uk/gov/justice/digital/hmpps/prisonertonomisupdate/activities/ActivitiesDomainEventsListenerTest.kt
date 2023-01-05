package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

internal class ActivitiesDomainEventsListenerTest {
  private val activitiesService: ActivitiesService = mock()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val listener =
    ActivitiesDomainEventListener(
      activitiesService,
      objectMapper,
      eventFeatureSwitch,
      telemetryClient
    )

  @Nested
  inner class Activities {
    @Nested
    inner class WhenEnabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any())).thenReturn(true)
      }

      @Test
      fun `will call service with create data`() {
        listener.onChange(message = activityCreatedMessage(123L))

        verify(activitiesService).createActivity(
          check {
            assertThat(it.identifier).isEqualTo(123L)
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
      fun `will not call service`() {
        listener.onChange(message = activityCreatedMessage(123L))

        verifyNoInteractions(activitiesService)
      }
    }

    @Nested
    inner class Retries {
      @Test
      fun `will call retry service with context data`() {
        listener.onChange(message = activityRetryMessage())

        verify(activitiesService).createRetry(
          check {
            assertThat(it.nomisCourseActivityId).isEqualTo(15)
            assertThat(it.activityScheduleId).isEqualTo(12345)
          }
        )

        verify(telemetryClient).trackEvent(
          eq("activity-retry-received"),
          check {
            assertThat(it["activityScheduleId"]).isEqualTo("12345")
          },
          isNull()
        )
      }
    }
  }
}
