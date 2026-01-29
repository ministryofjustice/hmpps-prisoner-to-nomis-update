package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension.Companion.incidentsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
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

  @Nested
  @DisplayName("GET /incident-reports/reference/{nomisIncidentId}/with-details")
  inner class GetIncidentDetailsByNomisId {
    @BeforeEach
    internal fun setUp() {
      incidentsDpsApi.stubGetIncidentByNomisId(1234)

      runBlocking {
        apiService.getIncidentDetailsByNomisId(1234)
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      incidentsDpsApi.verify(
        getRequestedFor(urlEqualTo("/incident-reports/reference/1234/with-details"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will retrieve incident data from the api`() {
      runBlocking {
        val incident = apiService.getIncidentDetailsByNomisId(1234)

        with(incident) {
          assertThat(id).isNotNull()
          assertThat(reportReference).isEqualTo("1234")
          assertThat(type).isEqualTo(ReportWithDetails.Type.ATTEMPTED_ESCAPE_FROM_ESCORT_1)
          assertThat(incidentDateAndTime).isEqualTo("2021-07-05T10:35:17")
          assertThat(title).isEqualTo("There was an incident in the exercise yard")
          assertThat(description).isEqualTo("Fred and Jimmy were fighting outside.")
          assertThat(reportedBy).isEqualTo("FSTAFF_GEN")
          assertThat(reportedAt).isEqualTo("2021-07-07T10:35:17.12345")
          assertThat(status).isEqualTo(ReportWithDetails.Status.AWAITING_REVIEW)
          assertThat(createdAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(modifiedAt).isEqualTo("2021-07-15T10:35:17")
          assertThat(modifiedBy).isEqualTo("JSMITH")
          assertThat(createdInNomis).isEqualTo(false)
          assertThat(prisonersInvolved[0].prisonerNumber).isEqualTo("A1234BC")
          assertThat(questions[0].question).isEqualTo("Was anybody hurt?")
          assertThat(questions[0].responses[0].response).isEqualTo("Yes")
        }
      }
    }
  }

  @Nested
  @DisplayName("GET /incident-reports")
  inner class GetIncidentsByAgencyAndStatus {

    @BeforeEach
    internal fun setUp() {
      incidentsDpsApi.stubGetIncidentCounts(5, 5)
    }

    @Nested
    inner class OpenIncidents {
      @BeforeEach
      internal fun setUp() {
        runBlocking {
          apiService.getOpenIncidentsCount(agencyId = "ASI")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        incidentsDpsApi.verify(
          getRequestedFor(urlPathEqualTo("/incident-reports"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `should pass location, size and status`() {
        incidentsDpsApi.verify(
          getRequestedFor(urlPathEqualTo("/incident-reports"))
            .withQueryParam("location", equalTo("ASI"))
            .withQueryParam("status", havingExactly("AWAITING_REVIEW", "NEEDS_UPDATING", "ON_HOLD", "UPDATED"))
            .withQueryParam("size", equalTo("1"))
            .withQueryParam("^(?!location$|status$|size$).+", absent()),
        )
      }

      @Test
      fun `will retrieve paged incidents from the api`() {
        runBlocking {
          assertThat(apiService.getOpenIncidentsCount(agencyId = "ASI")).isEqualTo(5L)
        }
      }
    }

    @Nested
    inner class ClosedIncidents {
      @BeforeEach
      internal fun setUp() {
        runBlocking {
          apiService.getClosedIncidentsCount(agencyId = "ASI")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        incidentsDpsApi.verify(
          getRequestedFor(urlPathEqualTo("/incident-reports"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `should pass location, size and status`() {
        incidentsDpsApi.verify(
          getRequestedFor(urlPathEqualTo("/incident-reports"))
            .withQueryParam("location", equalTo("ASI"))
            .withQueryParam("status", havingExactly("CLOSED", "DUPLICATE", "NOT_REPORTABLE", "REOPENED", "WAS_CLOSED"))
            .withQueryParam("size", equalTo("1"))
            .withQueryParam("^(?!location$|status$|size$).+", absent()),
        )
      }

      @Test
      fun `will retrieve paged incidents from the api`() {
        runBlocking {
          assertThat(apiService.getClosedIncidentsCount(agencyId = "ASI")).isEqualTo(5L)
        }
      }
    }
  }
}
