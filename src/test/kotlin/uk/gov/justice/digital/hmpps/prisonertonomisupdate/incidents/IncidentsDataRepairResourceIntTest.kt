package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension.Companion.incidentsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@ExtendWith(MockitoExtension::class)
class IncidentsDataRepairResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var incidentsNomisApi: IncidentsNomisApiMockServer

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  private companion object {
    private const val INCIDENT_ID: Long = 33
  }

  @DisplayName("POST /incidents/{incidentId}/repair")
  @Nested
  inner class RepairIncident {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      incidentsDpsApi.stubGetIncidentByNomisId(INCIDENT_ID)
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/incidents/{incidentId}/repair", INCIDENT_ID)
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/incidents/{incidentId}/repair", INCIDENT_ID)
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/incidents/{incidentId}/repair", INCIDENT_ID)
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class FailurePaths {
      @Test
      fun `repair incident NOMIS in charge of incident`() {
        nomisApi.stubCheckAgencySwitchForAgencyNotFound("INCIDENTS", "ASI")
        webTestClient.post().uri("/incidents/{incidentId}/repair", INCIDENT_ID)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      fun `draft incident doesn't get synced`() {
        nomisApi.stubCheckAgencySwitchForAgency("INCIDENTS", "ASI")
        incidentsDpsApi.stubGetIncidentByNomisId(INCIDENT_ID, response = dpsIncident().copy(status = ReportWithDetails.Status.DRAFT))
        webTestClient.post().uri("/incidents/{incidentId}/repair", INCIDENT_ID)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().is5xxServerError
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Unexpected error: DRAFT")
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `repair incident`() {
        nomisApi.stubCheckAgencySwitchForAgency("INCIDENTS", "ASI")
        incidentsNomisApi.stubUpsertIncident(INCIDENT_ID)

        webTestClient.post().uri("/incidents/{incidentId}/repair", INCIDENT_ID)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isNoContent

        verify(telemetryClient, times(1)).trackEvent(
          eq("incident-repair-success"),
          telemetryCaptor.capture(),
          isNull(),
        )

        val telemetry = telemetryCaptor.value
        assertThat(telemetry["incidentId"]).isEqualTo(INCIDENT_ID.toString())
      }
    }
  }
}
