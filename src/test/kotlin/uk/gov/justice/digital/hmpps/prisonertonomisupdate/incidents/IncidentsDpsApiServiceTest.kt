package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension.Companion.incidentsDpsApi
import java.util.UUID

@SpringAPIServiceTest
@Import(IncidentsDpsApiService::class, IncidentsConfiguration::class)
class IncidentsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: IncidentsDpsApiService

  @Nested
  inner class GetIncident {
    private val dpsIncidentId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsDpsApi.stubGetIncident()

      apiService.getIncident(incidentId = dpsIncidentId)

      incidentsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass incident Id to service`() = runTest {
      incidentsDpsApi.stubGetIncident()

      apiService.getIncident(incidentId = dpsIncidentId)

      incidentsDpsApi.verify(
        getRequestedFor(urlEqualTo("/incident-reports/$dpsIncidentId/with-details")),
      )
    }

    @Test
    fun `will return incident`() = runTest {
      incidentsDpsApi.stubGetIncident(incident = dpsIncident().copy(id = UUID.fromString(dpsIncidentId), title = "There was a problem"))

      val incident = apiService.getIncident(incidentId = dpsIncidentId)

      assertThat(incident.id.toString()).isEqualTo(dpsIncidentId)
      assertThat(incident.title).isEqualTo("There was a problem")
    }
  }
}
