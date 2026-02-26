package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class OfficialVisitsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateVisitMapping() {
    mappingServer.stubFor(
      post(urlEqualTo("/mapping/official-visits/visit")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateVisitMappingFailureFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/official-visits/visit")
        .inScenario("Retry CreateVisitMapping Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause CreateVisitMapping Success"),
    )

    mappingServer.stubFor(
      post("/mapping/official-visits/visit")
        .inScenario("Retry CreateVisitMapping Scenario")
        .whenScenarioStateIs("Cause CreateVisitMapping Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
  fun stubCreateVisitMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/official-visits/visit").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubCreateVisitorMapping() {
    mappingServer.stubFor(
      post(urlEqualTo("/mapping/official-visits/visitor")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateVisitorMappingFailureFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/official-visits/visitor")
        .inScenario("Retry CreateVisitorMapping Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause CreateVisitorMapping Success"),
    )

    mappingServer.stubFor(
      post("/mapping/official-visits/visitor")
        .inScenario("Retry CreateVisitorMapping Scenario")
        .whenScenarioStateIs("Cause CreateVisitorMapping Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
  fun stubCreateVisitorMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/official-visits/visitor").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetVisitByNomisIdsOrNull(
    nomisVisitId: Long = 1234L,
    mapping: OfficialVisitMappingDto? = OfficialVisitMappingDto(
      dpsId = "123456",
      nomisId = nomisVisitId,
      mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetVisitByDpsIdsOrNull(
    dpsVisitId: Long = 1234L,
    mapping: OfficialVisitMappingDto? = OfficialVisitMappingDto(
      dpsId = dpsVisitId.toString(),
      nomisId = 544321,
      mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/official-visits/visit/dps-id/$dpsVisitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/official-visits/visit/dps-id/$dpsVisitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetVisitorByDpsIdsOrNull(
    dpsVisitorId: Long = 1234L,
    mapping: OfficialVisitorMappingDto? = OfficialVisitorMappingDto(
      dpsId = dpsVisitorId.toString(),
      nomisId = 544321,
      mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/official-visits/visitor/dps-id/$dpsVisitorId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/official-visits/visitor/dps-id/$dpsVisitorId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
