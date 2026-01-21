@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.retryVisitsCreateMappingMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import java.time.LocalDate

@JsonTest
internal class VisitsDomainEventsListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val visitsService: VisitsService = mock()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val listener =
    VisitsDomainEventListener(
      visitsService,
      jsonMapper,
      eventFeatureSwitch,
      telemetryClient,
    )

  @Nested
  inner class Visits {
    @Nested
    inner class WhenEnabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any(), eq("visits"))).thenReturn(true)
      }

      @Test
      internal fun `will call service with create visit data`() = runTest {
        listener.onMessage(
          rawMessage = prisonVisitCreatedMessage(
            visitId = "99",
            occurredAt = "2021-03-08T11:23:56.031Z",
          ),
        ).join()

        verify(visitsService).createVisit(
          org.mockito.kotlin.check {
            Assertions.assertThat(it.reference).isEqualTo("99")
            Assertions.assertThat(it.bookingDate).isEqualTo(LocalDate.parse("2021-03-08"))
          },
        )
      }
    }

    @Nested
    inner class WhenDisabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any(), any())).thenReturn(false)
      }

      @Test
      internal fun `will not call service`() {
        listener.onMessage(
          rawMessage = prisonVisitCreatedMessage(
            visitId = "99",
            occurredAt = "2021-03-08T11:23:56.031Z",
          ),
        ).join()

        verifyNoInteractions(visitsService)
      }
    }

    @Nested
    inner class Retries {
      @Test
      internal fun `will call retry service with visit context data`() = runTest {
        listener.onMessage(rawMessage = retryVisitsCreateMappingMessage()).join()

        verify(visitsService).retryCreateMapping(any())
      }
    }
  }
}
