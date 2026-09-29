package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class ReligionMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetReligionMappings(mappings: List<ReligionMappingDto>) {
    mappingServer.stubFor(
      get(urlPathEqualTo("/mapping/core-person-religion/religion/cpr-ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mappings)),
        ),
    )
  }

  fun stubGetReligionMapping(cprId: String, mapping: ReligionMappingDto? = religionMapping(cprId)) {
    mappingServer.stubFor(
      get(urlPathEqualTo("/mapping/core-person-religion/religion/cpr-id/$cprId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(if (mapping == null) HttpStatus.NOT_FOUND.value() else HttpStatus.OK.value())
            .apply {
              if (mapping != null) withBody(jsonMapper.writeValueAsString(mapping))
            },
        ),
    )
  }

  fun stubCreateReligionMapping() {
    mappingServer.stubFor(
      post(urlEqualTo("/mapping/core-person-religion/religion"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}

private fun religionMapping(cprId: String) = ReligionMappingDto(
  cprId = cprId,
  nomisId = 12345L,
  nomisPrisonNumber = "A1234AA",
  mappingType = ReligionMappingDto.MappingType.DPS_CREATED,
)
