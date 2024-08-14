package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonDpsApiExtension.Companion.prisonPersonDpsApi

class PrisonPersonIntTest(
  // TODO remove this and replace with event processing after creating a message handler
  @Autowired private val service: PrisonPersonService,
) : IntegrationTestBase() {

  @Autowired
  private lateinit var prisonPersonNomisApi: PrisonPersonNomisApiMockServer

  @Nested
  inner class UpdatePhysicalAttributes {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        prisonPersonDpsApi.stubGetPrisonPerson(prisonerNumber = "A1234AA")
        prisonPersonNomisApi.stubPutPhysicalAttributes(offenderNo = "A1234AA")

        service.updatePhysicalAttributes(offenderNo = "A1234AA")
      }

      @Test
      fun `should call DPS API`() {
        prisonPersonDpsApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/A1234AA")),
        )
      }

      @Test
      fun `should call NOMIS API`() {
        prisonPersonNomisApi.verify(
          putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/physical-attributes"))
            .withRequestBody(matchingJsonPath("$.height", equalTo("180")))
            .withRequestBody(matchingJsonPath("$.weight", equalTo("80"))),
        )
      }

      @Test
      fun `should publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("physical-attributes-update-success"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "bookingId" to "12345",
                "created" to "true",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Failures {
      @Disabled("cannot implement this until we add message handling (source system is contained in the message)")
      @Test
      fun `should ignore if source system is not DPS`() {}

      @Test
      fun `should throw if call to DPS fails`() = runTest {
        prisonPersonDpsApi.stubGetPrisonPerson(HttpStatus.BAD_REQUEST)

        assertThrows<WebClientResponseException.BadRequest> {
          service.updatePhysicalAttributes(offenderNo = "A1234AA")
        }

        verify(telemetryClient).trackEvent(
          eq("physical-attributes-update-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "400 Bad Request from GET http://localhost:8097/prisoners/A1234AA",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `should throw if call to NOMIS fails`() = runTest {
        prisonPersonDpsApi.stubGetPrisonPerson(prisonerNumber = "A1234AA")
        prisonPersonNomisApi.stubPutPhysicalAttributes(HttpStatus.BAD_GATEWAY)

        assertThrows<WebClientResponseException.BadGateway> {
          service.updatePhysicalAttributes(offenderNo = "A1234AA")
        }

        verify(telemetryClient).trackEvent(
          eq("physical-attributes-update-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A1234AA",
                "reason" to "502 Bad Gateway from PUT http://localhost:8082/prisoners/A1234AA/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }
    }
  }
}
