package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class IncidentsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    fun createIncidentRequest(): CreateIncidentRequest = CreateIncidentRequest(
      title = "An incident occurred",
      description = "Fighting and shouting occurred in the prisoner's cell and a chair was thrown.",
      location = "BXI",
      statusCode = "AWAN",
      typeCode = "INACTIVE",
      incidentDateTime = LocalDateTime.parse("2023-12-30T13:45"),
      reportedDateTime = LocalDateTime.parse("2024-01-02T09:30"),
      reportedBy = "A_USER",
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun stubCreateIncident(
    incidentId: Long = 123456,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/incidents/$incidentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubDeleteIncident(
    incidentId: Long = 123456,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/incidents/$incidentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
}

fun nomisAudit() = NomisAudit(
  createDatetime = LocalDateTime.now(),
  createUsername = "Q1251T",
)
