package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsNomisApiMockServer.Companion.upsertIncidentRequest
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
  }
}
