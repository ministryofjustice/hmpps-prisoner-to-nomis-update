@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.corePersonMergeMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.religionCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

@JsonTest
internal class CorePersonDomainEventsListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val religionService: ReligionService = mock()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val corePersonMergeService: CorePersonMergeService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val listener =
    CorePersonDomainEventListener(
      jsonMapper,
      eventFeatureSwitch,
      religionService,
      corePersonMergeService,
      telemetryClient,
    )

  @Nested
  inner class CorePerson {
    @Nested
    inner class WhenEnabled {
      @BeforeEach
      internal fun setUp() {
        whenever(eventFeatureSwitch.isEnabled(any(), eq("coreperson"))).thenReturn(true)
      }

      @Test
      internal fun `will call religion service with create religion data`() = runTest {
        listener.onMessage(
          rawMessage = religionCreatedMessage("A1234BC", "e312a74d-ca98-4fbc-b212-608bc41558e7"),
        ).join()

        verify(religionService).religionCreated(
          check { it ->
            assertThat(it.additionalInformation.cprReligionId.toString()).isEqualTo("e312a74d-ca98-4fbc-b212-608bc41558e7")
            assertThat(it.personReference.identifiers.first { it.type == "prisonNumber" }.value).isEqualTo("A1234BC")
          },
        )
      }

      @Test
      internal fun `will call core person merge service with create religion data`() = runTest {
        listener.onMessage(
          rawMessage = corePersonMergeMessage("A1234BC"),
        ).join()

        verify(corePersonMergeService).mergePerson(
          check {
            assertThat(it.personReferenceTo.identifiers.first { it.type == "prisonNumber" }.value).isEqualTo("A1234BC")
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
          rawMessage = religionCreatedMessage("A1234BC", "e312a74d-ca98-4fbc-b212-608bc41558e7"),
        ).join()
        listener.onMessage(
          rawMessage = corePersonMergeMessage("A1234BC"),
        ).join()

        verifyNoInteractions(religionService)
        verifyNoInteractions(corePersonMergeService)
      }
    }
  }
}
