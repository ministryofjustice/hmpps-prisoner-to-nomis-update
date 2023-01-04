package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.IncentivesService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService
import java.time.LocalDate

internal class PrisonerDomainEventsListenerTest {
  private val prisonVisitsService: PrisonVisitsService = mock()
  private val incentivesService: IncentivesService = mock()
  private val objectMapper: ObjectMapper = objectMapper()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()

  private val listener =
    PrisonerDomainEventsListener(
      prisonVisitsService,
      incentivesService,
      objectMapper,
      eventFeatureSwitch,
    )

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
            assertThat(it.reference).isEqualTo("99")
            assertThat(it.bookingDate).isEqualTo(LocalDate.parse("2021-03-08"))
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
          message = prisonVisitCreatedMessage(
            visitId = "99",
            occurredAt = "2021-03-08T11:23:56.031Z"
          )
        )

        verifyNoInteractions(prisonVisitsService)
      }
    }
  }

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
  }
}
