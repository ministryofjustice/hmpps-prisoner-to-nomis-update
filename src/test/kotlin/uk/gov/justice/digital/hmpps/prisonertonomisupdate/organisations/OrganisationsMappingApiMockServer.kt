package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class OrganisationsMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetByDpsOrganisationId(
    dpsOrganisationId: String = "565643",
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = dpsOrganisationId,
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }
  fun stubGetByDpsOrganisationId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(WireMock.urlPathMatching("/mapping/corporate/organisation/dps-organisation-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteByDpsOrganisationId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      delete(urlMatching("/mapping/corporate/organisation/dps-organisation-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubDeleteByDpsOrganisationId(dpsOrganisationId: String) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")).willReturn(
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
