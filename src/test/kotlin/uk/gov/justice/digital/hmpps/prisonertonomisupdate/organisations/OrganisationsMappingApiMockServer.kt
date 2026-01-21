package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

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
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class OrganisationsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetByDpsOrganisationIdOrNull(
    dpsOrganisationId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsOrganisationId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsOrganisationId(
    dpsOrganisationId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsOrganisationId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsOrganisationIdOrNull(dpsOrganisationId, mapping)

  fun stubDeleteByNomisOrganisationId(
    nomisOrganisationId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/organisation/nomis-organisation-id/$nomisOrganisationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateOrganisationMapping() {
    mappingServer.stubFor(
      post("/mapping/corporate/organisation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateOrganisationMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/corporate/organisation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOrganisationMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/corporate/organisation")
        .inScenario("Retry Mapping Organisation Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Organisation Success"),
    )

    mappingServer.stubFor(
      post("/mapping/corporate/organisation")
        .inScenario("Retry Mapping Organisation Scenario")
        .whenScenarioStateIs("Cause Mapping Organisation Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsAddressIdOrNull(
    dpsAddressId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsAddressId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/address/dps-address-id/$dpsAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/address/dps-address-id/$dpsAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsAddressId(
    dpsAddressId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsAddressId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsAddressIdOrNull(dpsAddressId, mapping)

  fun stubDeleteByNomisAddressId(
    nomisAddressId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateAddressMapping() {
    mappingServer.stubFor(
      post("/mapping/corporate/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAddressMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/corporate/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateAddressMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/corporate/address")
        .inScenario("Retry Mapping Address Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Address Success"),
    )

    mappingServer.stubFor(
      post("/mapping/corporate/address")
        .inScenario("Retry Mapping Address Scenario")
        .whenScenarioStateIs("Cause Mapping Address Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsPhoneIdOrNull(
    dpsPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsPhoneId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/phone/dps-phone-id/$dpsPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/phone/dps-phone-id/$dpsPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsPhoneId(
    dpsPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsPhoneId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsPhoneIdOrNull(dpsPhoneId, mapping)

  fun stubDeleteByNomisPhoneId(
    nomisPhoneId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreatePhoneMapping() {
    mappingServer.stubFor(
      post("/mapping/corporate/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePhoneMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/corporate/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePhoneMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/corporate/phone")
        .inScenario("Retry Mapping Phone Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Phone Success"),
    )

    mappingServer.stubFor(
      post("/mapping/corporate/phone")
        .inScenario("Retry Mapping Phone Scenario")
        .whenScenarioStateIs("Cause Mapping Phone Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsEmailIdOrNull(
    dpsEmailId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsEmailId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/email/dps-email-id/$dpsEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/email/dps-email-id/$dpsEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsEmailId(
    dpsEmailId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsEmailId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsEmailIdOrNull(dpsEmailId, mapping)

  fun stubDeleteByNomisEmailId(
    nomisEmailId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisEmailId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateEmailMapping() {
    mappingServer.stubFor(
      post("/mapping/corporate/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateEmailMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/corporate/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateEmailMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/corporate/email")
        .inScenario("Retry Mapping Email Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Email Success"),
    )

    mappingServer.stubFor(
      post("/mapping/corporate/email")
        .inScenario("Retry Mapping Email Scenario")
        .whenScenarioStateIs("Cause Mapping Email Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsWebIdOrNull(
    dpsWebId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsWebId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/web/dps-web-id/$dpsWebId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/web/dps-web-id/$dpsWebId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsWebId(
    dpsWebId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsWebId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsWebIdOrNull(dpsWebId, mapping)

  fun stubDeleteByNomisWebId(
    nomisWebId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisWebId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateWebMapping() {
    mappingServer.stubFor(
      post("/mapping/corporate/web").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateWebMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/corporate/web").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateWebMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/corporate/web")
        .inScenario("Retry Mapping Web Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Web Success"),
    )

    mappingServer.stubFor(
      post("/mapping/corporate/web")
        .inScenario("Retry Mapping Web Scenario")
        .whenScenarioStateIs("Cause Mapping Web Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  } fun stubGetByDpsAddressPhoneIdOrNull(
    dpsAddressPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsAddressPhoneId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/address-phone/dps-address-phone-id/$dpsAddressPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/corporate/address-phone/dps-address-phone-id/$dpsAddressPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsAddressPhoneId(
    dpsAddressPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 654321,
      dpsId = dpsAddressPhoneId.toString(),
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsAddressPhoneIdOrNull(dpsAddressPhoneId, mapping)

  fun stubDeleteByNomisAddressPhoneId(
    nomisAddressPhoneId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisAddressPhoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateAddressPhoneMapping() {
    mappingServer.stubFor(
      post("/mapping/corporate/address-phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAddressPhoneMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/corporate/address-phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateAddressPhoneMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/corporate/address-phone")
        .inScenario("Retry Mapping AddressPhone Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping AddressPhone Success"),
    )

    mappingServer.stubFor(
      post("/mapping/corporate/address-phone")
        .inScenario("Retry Mapping AddressPhone Scenario")
        .whenScenarioStateIs("Cause Mapping AddressPhone Success")
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
