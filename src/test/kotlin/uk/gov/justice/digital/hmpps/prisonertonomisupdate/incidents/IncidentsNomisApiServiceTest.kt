package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsNomisApiMockServer.Companion.upsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  IncidentsNomisApiService::class,
  IncidentsNomisApiMockServer::class,
  RetryApiService::class,
)
class IncidentsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: IncidentsNomisApiService

  @Autowired
  private lateinit var mockServer: IncidentsNomisApiMockServer

  @Nested
  inner class Incident {

    @Nested
    inner class UpsertIncident {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpsertIncident()

        apiService.upsertIncident(nomisId = 123456, request = upsertIncidentRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubUpsertIncident()

        apiService.upsertIncident(nomisId = 123456, request = upsertIncidentRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/incidents/123456")),
        )
      }
    }

    @Nested
    inner class DeleteIncident {
      private val nomisId = 17171L

      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteIncident(nomisId)

        apiService.deleteIncident(nomisId)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteIncident(nomisId)

        apiService.deleteIncident(nomisId)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/incidents/$nomisId")),
        )
      }
    }

    @Nested
    @DisplayName("GET /incidents/{nomisIncidentId}")
    inner class GetIncident {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetIncident()

        apiService.getIncident(1234L)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS ids to service`() = runTest {
        mockServer.stubGetIncident()

        apiService.getIncident(1234L)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/incidents/1234")),
        )
      }

      @Test
      fun `will return an incident`() = runTest {
        mockServer.stubGetIncident()

        val incident = apiService.getIncident(1234L)

        assertThat(incident.incidentId).isEqualTo(1234)
        assertThat(incident.status.code).isEqualTo("AWAN")
        assertThat(incident.incidentDateTime).isEqualTo("2017-04-12T16:45:00")
      }

      @Test
      fun `will throw error when incident does not exist`() = runTest {
        mockServer.stubGetIncident(NOT_FOUND)

        assertThrows<WebClientResponseException.NotFound> {
          apiService.getIncident(1234L)
        }
      }

      @Test
      fun `will throw error when API returns an error`() = runTest {
        mockServer.stubGetIncident(INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.getIncident(1234L)
        }
      }
    }

    @Nested
    @DisplayName("GET /incidents/reconciliation/agencies")
    inner class GetAgenciesWithIncidents {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetIncidentAgencies()

        apiService.getAgenciesWithIncidents()

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will return agencies`() = runTest {
        mockServer.stubGetIncidentAgencies()

        val agencies = apiService.getAgenciesWithIncidents()

        assertThat(agencies.size).isEqualTo(3)
        assertThat(agencies[0].agencyId).isEqualTo("ASI")
        assertThat(agencies[1].agencyId).isEqualTo("BFI")
        assertThat(agencies[2].agencyId).isEqualTo("WWI")
      }

      @Test
      internal fun `will call the Nomis reconciliation endpoint`() = runTest {
        mockServer.stubGetIncidentAgencies()

        apiService.getAgenciesWithIncidents()

        mockServer.verify(getRequestedFor(urlPathEqualTo("/incidents/reconciliation/agencies")))
      }
    }

    @Nested
    @DisplayName("GET /incidents/reconciliation/agency/{agencyId}/counts")
    inner class GetAgencyIncidentCounts {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetReconciliationAgencyIncidentCounts()

        apiService.getAgencyIncidentCounts("ASI")

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS agency id to service`() = runTest {
        mockServer.stubGetReconciliationAgencyIncidentCounts()

        apiService.getAgencyIncidentCounts("ASI")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/incidents/reconciliation/agency/ASI/counts")),
        )
      }

      @Test
      fun `will return agency incident counts`() = runTest {
        mockServer.stubGetReconciliationAgencyIncidentCounts(closed = 4)

        val agencyCount = apiService.getAgencyIncidentCounts("ASI")

        assertThat(agencyCount.agencyId).isEqualTo("ASI")
        assertThat(agencyCount.incidentCount.openIncidents).isEqualTo(3)
        assertThat(agencyCount.incidentCount.closedIncidents).isEqualTo(4)
      }
    }

    @Nested
    @DisplayName("GET /incidents/reconciliation/agency/{agencyId}/ids")
    inner class GetReconciliationOpenIncidentIds {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)

        apiService.getOpenIncidentIds("ASI", 2, 5)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS agency id to service`() = runTest {
        mockServer.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)

        apiService.getOpenIncidentIds("ASI", 2, 5)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/incidents/reconciliation/agency/ASI/ids")),
        )
      }

      @Test
      fun `will return open incident Ids`() = runTest {
        mockServer.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)

        val incidentIds = apiService.getOpenIncidentIds("ASI", 2, 5)
        assertThat(incidentIds.size).isEqualTo(3)
        assertThat(incidentIds).extracting<Long>(IncidentIdResponse::incidentId).containsExactly(33, 34, 35)
      }
    }
  }
}
