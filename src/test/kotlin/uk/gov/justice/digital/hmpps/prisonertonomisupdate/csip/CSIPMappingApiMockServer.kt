package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import java.util.UUID

@Component
class CSIPMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByDpsReportId(
    dpsCSIPReportId: String = UUID.randomUUID().toString(),
    mapping: CSIPFullMappingDto = CSIPFullMappingDto(
      nomisCSIPReportId = 123456,
      dpsCSIPReportId = dpsCSIPReportId,
      attendeeMappings = listOf(),
      factorMappings = listOf(),
      interviewMappings = listOf(),
      planMappings = listOf(),
      reviewMappings = listOf(),
      mappingType = CSIPFullMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/csip/dps-csip-id/${mapping.dpsCSIPReportId}/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByDpsReportId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(WireMock.urlPathMatching("/mapping/csip/dps-csip-id/\\S+/all"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubPostChildrenMapping() {
    mappingServer.stubFor(
      post("/mapping/csip/children/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMapping() {
    mappingServer.stubFor(
      post("/mapping/csip/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }
  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/csip/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFollowedBySuccess(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/csip/all")
        .inScenario("Retry Mapping CSIP Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping CSIP Success"),
    )

    mappingServer.stubFor(
      post("/mapping/csip/all")
        .inScenario("Retry Mapping CSIP Scenario")
        .whenScenarioStateIs("Cause Mapping CSIP Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubPostChildrenMappingFollowedBySuccess(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/csip/children/all")
        .inScenario("Retry Mapping CSIP Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping CSIP Success"),
    )

    mappingServer.stubFor(
      post("/mapping/csip/children/all")
        .inScenario("Retry Mapping CSIP Scenario")
        .whenScenarioStateIs("Cause Mapping CSIP Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      delete(urlMatching("/mapping/csip/dps-csip-id/\\S+/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteByDpsId(
    dpsCSIPId: String = UUID.randomUUID().toString(),
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteChildMappingsByDpsId(
    dpsCSIPId: String = UUID.randomUUID().toString(),
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/children")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
