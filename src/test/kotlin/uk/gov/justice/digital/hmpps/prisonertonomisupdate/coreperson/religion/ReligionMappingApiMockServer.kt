package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
