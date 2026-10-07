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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact.CorePersonContactService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.contactCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.contactUpdatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.corePersonMergeMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.religionCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

@JsonTest
internal class CorePersonDomainEventsListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val religionService: ReligionService = mock()
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val corePersonMergeService: CorePersonMergeService = mock()
  private val corePersonContactService: CorePersonContactService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val listener =
    CorePersonDomainEventListener(
      jsonMapper,
      eventFeatureSwitch,
      religionService,
      corePersonMergeService,
      corePersonContactService,
      CorePersonRetryService(jsonMapper, religionService, corePersonContactService),
      telemetryClient,
    )

  @Nested
  inner class RetryCreateMapping {
    @Test
    internal fun `will route religion mapping retries to the religion service`() = runTest {
      val retryMessage = """{"mapping":{"cprId":"e312a74d-ca98-4fbc-b212-608bc41558e7"},"telemetryAttributes":{},"entityName":"core-person-religion"}"""

      listener.onMessage(rawMessage = retryMessage(retryMessage)).join()

      verify(religionService).retryCreateMapping(retryMessage)
      verifyNoInteractions(corePersonContactService)
    }

    @Test
    internal fun `will route contact mapping retries to the contact service`() = runTest {
      val retryMessage = """{"mapping":{"cprId":"11111111-2222-3333-4444-555555555555"},"telemetryAttributes":{},"entityName":"core-person-contact"}"""

      listener.onMessage(rawMessage = retryMessage(retryMessage)).join()

      verify(corePersonContactService).retryCreateMapping(retryMessage)
      verifyNoInteractions(religionService)
    }

    private fun retryMessage(message: String) = jsonMapper.writeValueAsString(
      mapOf("Type" to "RETRY_CREATE_MAPPING", "Message" to message),
    )
  }

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
          rawMessage = religionCreatedMessage("A1234BC", "e312a74d-ca98-4fbc-b212-608bc41558e7", source = "core-person-record"),
        ).join()

        verify(religionService).religionCreated(
          check { it ->
            assertThat(it.additionalInformation.cprReligionId.toString()).isEqualTo("e312a74d-ca98-4fbc-b212-608bc41558e7")
            assertThat(it.personReference.identifiers.first { it.type == "prisonNumber" }.value).isEqualTo("A1234BC")
          },
          eq(EventSource(value = "core-person-record", type = "String")),
        )
      }

      @Test
      internal fun `will call contact service with create contact data`() = runTest {
        listener.onMessage(
          rawMessage = contactCreatedMessage("A1234BC", "11111111-2222-3333-4444-555555555555", source = "core-person-record"),
        ).join()

        verify(corePersonContactService).contactCreated(
          check { it ->
            assertThat(it.additionalInformation.cprContactId.toString()).isEqualTo("11111111-2222-3333-4444-555555555555")
            assertThat(it.personReference.identifiers.first { it.type == "prisonNumber" }.value).isEqualTo("A1234BC")
          },
          eq(EventSource(value = "core-person-record", type = "String")),
        )
        verifyNoInteractions(religionService)
        verifyNoInteractions(corePersonMergeService)
      }

      @Test
      internal fun `will pass the nomis event source to contact service`() = runTest {
        listener.onMessage(
          rawMessage = contactCreatedMessage("A1234BC", "11111111-2222-3333-4444-555555555555", source = "nomis"),
        ).join()

        verify(corePersonContactService).contactCreated(
          check {
            assertThat(it.additionalInformation.cprContactId.toString()).isEqualTo("11111111-2222-3333-4444-555555555555")
          },
          eq(EventSource(value = "nomis", type = "String")),
        )
      }

      @Test
      internal fun `will call contact service with update contact data`() = runTest {
        listener.onMessage(
          rawMessage = contactUpdatedMessage("A1234BC", "11111111-2222-3333-4444-555555555555", source = "core-person-record"),
        ).join()

        verify(corePersonContactService).contactUpdated(
          check { it ->
            assertThat(it.additionalInformation.cprContactId.toString()).isEqualTo("11111111-2222-3333-4444-555555555555")
            assertThat(it.personReference.identifiers.first { it.type == "prisonNumber" }.value).isEqualTo("A1234BC")
          },
          eq(EventSource(value = "core-person-record", type = "String")),
        )
        verifyNoInteractions(religionService)
        verifyNoInteractions(corePersonMergeService)
      }

      @Test
      internal fun `will call core person merge service when the event has a null event source`() = runTest {
        listener.onMessage(
          rawMessage = corePersonMergeMessage("A1234BC", source = null),
        ).join()

        verify(corePersonMergeService).mergePerson(
          check {
            assertThat(it.personReference.identifiers.first { it.type == "toPrisonNumber" }.value).isEqualTo("A1234BC")
          },
        )
      }

      @Test
      internal fun `will call core person merge service when the event has an event source`() = runTest {
        listener.onMessage(
          rawMessage = corePersonMergeMessage("A1234BC", source = "core-person-record"),
        ).join()

        verify(corePersonMergeService).mergePerson(
          check {
            assertThat(it.personReference.identifiers.first { it.type == "toPrisonNumber" }.value).isEqualTo("A1234BC")
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
        listener.onMessage(
          rawMessage = contactCreatedMessage("A1234BC", "11111111-2222-3333-4444-555555555555"),
        ).join()
        listener.onMessage(
          rawMessage = contactUpdatedMessage("A1234BC", "11111111-2222-3333-4444-555555555555"),
        ).join()

        verifyNoInteractions(religionService)
        verifyNoInteractions(corePersonMergeService)
        verifyNoInteractions(corePersonContactService)
      }
    }
  }
}
