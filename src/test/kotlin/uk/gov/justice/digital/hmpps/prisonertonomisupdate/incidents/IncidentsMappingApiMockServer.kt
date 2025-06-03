package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class IncidentsMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetByDpsIncidentIdOrNull(
    dpsIncidentId: String = "123456",
    mapping: IncidentMappingDto? = IncidentMappingDto(
      nomisIncidentId = 654321,
      dpsIncidentId = dpsIncidentId,
      mappingType = IncidentMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/incidents/dps-incident-id/$dpsIncidentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/incidents/dps-incident-id/$dpsIncidentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsIncidentId(
    dpsIncidentId: String = "123456",
    mapping: IncidentMappingDto = IncidentMappingDto(
      nomisIncidentId = 654321,
      dpsIncidentId = dpsIncidentId,
      mappingType = IncidentMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsIncidentIdOrNull(dpsIncidentId, mapping)

  fun stubDeleteByDpsIncidentId(
    dpsIncidentId: String = "123456",
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/incidents/dps-incident-id/$dpsIncidentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateIncidentMapping() {
    mappingServer.stubFor(
      post("/mapping/incidents").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateIncidentMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/incidents").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateIncidentMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/incidents")
        .inScenario("Retry Mapping Incident Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Incident Success"),
    )

    mappingServer.stubFor(
      post("/mapping/incidents")
        .inScenario("Retry Mapping Incident Scenario")
        .whenScenarioStateIs("Cause Mapping Incident Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
