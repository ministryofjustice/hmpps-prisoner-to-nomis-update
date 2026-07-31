package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.NomisDpsLocationMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class PropertyMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByDpsId(
    dpsId: String,
    mapping: PropertyContainerMappingDto = PropertyContainerMappingDto(
      dpsPropertyContainerId = dpsId,
      nomisPropertyContainerId = 1,
      offenderNo = "A1234AA",
      bookingId = 123456,
      mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/property/dps-id/$dpsId")).willReturn(
        okJson(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/property/dps-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetLocationByDpsId(dpsLocationId: String, nomisLocationId: Long) {
    mappingServer.stubFor(
      get(urlPathMatching("/api/locations/dps/$dpsLocationId")).willReturn(
        okJson(
          jsonMapper.writeValueAsString(
            NomisDpsLocationMapping(dpsLocationId, nomisLocationId),
          ),
        ),
      ),
    )
  }

  fun stubPostMapping() {
    mappingServer.stubFor(
      post("/mapping/property").willReturn(
        status(201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/property").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFollowedBySuccess(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/property")
        .inScenario("Retry Mapping Property Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Property Success"),
    )

    mappingServer.stubFor(
      post("/mapping/property")
        .inScenario("Retry Mapping Property Scenario")
        .whenScenarioStateIs("Cause Mapping Property Success")
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
