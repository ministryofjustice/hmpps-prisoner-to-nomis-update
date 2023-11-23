package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

private const val bookingId = 1234L

class IncentivesDataRepairResourceIntTest : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("POST /incentives/prisoner/booking-id/{bookingId}/repair")
  @Nested
  inner class RepairIncentive {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      incentivesApi.stubCurrentIncentiveGet(bookingId, id = 8765, prisonerNumber = "A1234AA")
      incentivesApi.stubIncentiveGet(
        8765,
        """
          {
            "id": 8765,
            "iepCode": "STD",
            "iepLevel": "Standard",
            "bookingId": $bookingId,
            "sequence": 2,
            "iepDate": "2022-12-02",
            "iepTime": "2022-12-02T10:00:00",
            "agencyId": "MDI"
          }
        """.trimIndent(),
      )
      nomisApi.stubIncentiveCreate(bookingId)
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/incentives/prisoner/booking-id/{bookingId}/repair", bookingId)
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/incentives/prisoner/booking-id/{bookingId}/repair", bookingId)
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/incentives/prisoner/booking-id/{bookingId}/repair", bookingId)
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `repair incentive`() {
        webTestClient.post().uri("/incentives/prisoner/booking-id/{bookingId}/repair", bookingId)
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_INCENTIVES")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isOk

        verify(telemetryClient, times(1)).trackEvent(eq("incentives-repair"), telemetryCaptor.capture(), isNull())

        val telemetry = telemetryCaptor.value
        assertThat(telemetry["nomisBookingId"]).isEqualTo(bookingId.toString())
        assertThat(telemetry["nomisIncentiveSequence"]).isEqualTo("1")
        assertThat(telemetry["id"]).isEqualTo("8765")
        assertThat(telemetry["offenderNo"]).isEqualTo("A1234AA")
        assertThat(telemetry["iep"]).isEqualTo("STD")
      }
    }
  }
}
