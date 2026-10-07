package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class CorePersonContactMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetAddressMapping(cprAddressId: String, mapping: CorePersonAddressMappingDto? = addressMapping(cprAddressId)) {
    mappingServer.stubFor(
      get(urlPathEqualTo("/mapping/core-person/address/cpr-address-id/$cprAddressId"))
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

  fun stubGetContactMapping(cprContactId: String, mapping: CorePersonContactMappingDto? = contactMapping(cprContactId)) {
    mappingServer.stubFor(
      get(urlPathEqualTo("/mapping/core-person/contact/cpr-contact-id/$cprContactId"))
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

  fun stubCreateContactMapping() {
    mappingServer.stubFor(
      post(urlEqualTo("/mapping/core-person/contact"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
    )
  }

  fun stubCreateContactMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR) {
    mappingServer.stubFor(
      post(urlEqualTo("/mapping/core-person/contact"))
        .inScenario("Retry Mapping Core Person Contact Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = status.value()))),
        ).willSetStateTo("Cause Mapping Core Person Contact Success"),
    )
    mappingServer.stubFor(
      post(urlEqualTo("/mapping/core-person/contact"))
        .inScenario("Retry Mapping Core Person Contact Scenario")
        .whenScenarioStateIs("Cause Mapping Core Person Contact Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}

fun contactMapping(cprContactId: String) = CorePersonContactMappingDto(
  cprId = cprContactId,
  nomisId = 12345L,
  nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE,
  nomisPrisonNumber = "A1234AA",
  mappingType = CorePersonContactMappingDto.MappingType.CPR_CREATED,
)

private fun addressMapping(cprAddressId: String) = CorePersonAddressMappingDto(
  cprId = cprAddressId,
  nomisId = 12345L,
  nomisPrisonNumber = "A1234AA",
  mappingType = CorePersonAddressMappingDto.MappingType.CPR_CREATED,
)
