package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

internal class ActivitiesDomainEventsListenerTest {
  private val activitiesService: ActivitiesService = mock()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()

  private val listener =
    ActivitiesDomainEventListener(
      activitiesService,
      objectMapper,
      eventFeatureSwitch,
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
      fun `will call service with create data`() = runBlocking {
        listener.onMessage(rawMessage = activityCreatedMessage(123L)).join()

        verify(activitiesService).createActivity(
          check {
            assertThat(it.additionalInformation.activityScheduleId).isEqualTo(123L)
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
      fun `will not call service`() = runBlocking {
        listener.onMessage(rawMessage = activityCreatedMessage(123L)).join()

        verifyNoInteractions(activitiesService)
      }
    }

    @Nested
    inner class Retries {
      @Test
      fun `will call retry service with context data`() = runBlocking {
        listener.onMessage(rawMessage = activityRetryMessage()).join()

        verify(activitiesService).retryCreateMapping("""{"mapping": {"activityScheduleId":12345,"nomisCourseActivityId":15}, "telemetryAttributes": {}}""")
      }
    }
  }
}
