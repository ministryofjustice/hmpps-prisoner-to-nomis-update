package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class ContactPersonMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetByDpsContactIdOrNull(
    dpsContactId: Long = 123456,
    mapping: PersonMappingDto? = PersonMappingDto(
      nomisId = dpsContactId,
      dpsId = dpsContactId.toString(),
      mappingType = PersonMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/dps-contact-id/$dpsContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/dps-contact-id/$dpsContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreatePersonMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePersonMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePersonMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/person")
        .inScenario("Retry Mapping Person Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Person Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/person")
        .inScenario("Retry Mapping Person Scenario")
        .whenScenarioStateIs("Cause Mapping Person Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteByDpsContactId(dpsContactId: Long) {
    mappingServer.stubFor(
      delete("/mapping/contact-person/person/dps-contact-id/$dpsContactId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubGetByDpsPrisonerContactIdOrNull(
    dpsPrisonerContactId: Long = 123456,
    mapping: PersonContactMappingDto? = PersonContactMappingDto(
      nomisId = dpsPrisonerContactId,
      dpsId = dpsPrisonerContactId.toString(),
      mappingType = PersonContactMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact/dps-prisoner-contact-id/$dpsPrisonerContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact/dps-prisoner-contact-id/$dpsPrisonerContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByDpsPrisonerContactId(
    dpsPrisonerContactId: Long = 123456,
    mapping: PersonContactMappingDto? = PersonContactMappingDto(
      nomisId = dpsPrisonerContactId,
      dpsId = dpsPrisonerContactId.toString(),
      mappingType = PersonContactMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsPrisonerContactIdOrNull(dpsPrisonerContactId, mapping)

  fun stubCreateContactMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/contact").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateContactMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/contact").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateContactMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/contact")
        .inScenario("Retry Mapping Contact Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Contact Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/contact")
        .inScenario("Retry Mapping Contact Scenario")
        .whenScenarioStateIs("Cause Mapping Contact Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsContactAddressIdOrNull(
    dpsContactAddressId: Long = 123456,
    mapping: PersonAddressMappingDto? = PersonAddressMappingDto(
      nomisId = 654321,
      dpsId = dpsContactAddressId.toString(),
      mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/dps-contact-address-id/$dpsContactAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/dps-contact-address-id/$dpsContactAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactAddressId(
    dpsContactAddressId: Long = 123456,
    mapping: PersonAddressMappingDto = PersonAddressMappingDto(
      nomisId = 654321,
      dpsId = dpsContactAddressId.toString(),
      mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactAddressIdOrNull(dpsContactAddressId, mapping)

  fun stubCreateAddressMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAddressMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateAddressMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/address")
        .inScenario("Retry Mapping Address Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Address Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/address")
        .inScenario("Retry Mapping Address Scenario")
        .whenScenarioStateIs("Cause Mapping Address Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsContactEmailIdOrNull(
    dpsContactEmailId: Long = 123456,
    mapping: PersonEmailMappingDto? = PersonEmailMappingDto(
      nomisId = 654321,
      dpsId = dpsContactEmailId.toString(),
      mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/email/dps-contact-email-id/$dpsContactEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/email/dps-contact-email-id/$dpsContactEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreateEmailMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateEmailMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateEmailMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/email")
        .inScenario("Retry Mapping Email Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Email Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/email")
        .inScenario("Retry Mapping Email Scenario")
        .whenScenarioStateIs("Cause Mapping Email Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsContactPhoneIdOrNull(
    dpsContactPhoneId: Long = 123456,
    mapping: PersonPhoneMappingDto? = PersonPhoneMappingDto(
      nomisId = 654321,
      dpsId = dpsContactPhoneId.toString(),
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
      mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/dps-contact-phone-id/$dpsContactPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/dps-contact-phone-id/$dpsContactPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByDpsContactAddressPhoneIdOrNull(
    dpsContactAddressPhoneId: Long = 123456,
    mapping: PersonPhoneMappingDto? = PersonPhoneMappingDto(
      nomisId = 654321,
      dpsId = dpsContactAddressPhoneId.toString(),
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
      mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/dps-contact-address-phone-id/$dpsContactAddressPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/dps-contact-address-phone-id/$dpsContactAddressPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreatePhoneMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePhoneMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePhoneMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/phone")
        .inScenario("Retry Mapping Phone Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Phone Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/phone")
        .inScenario("Retry Mapping Phone Scenario")
        .whenScenarioStateIs("Cause Mapping Phone Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsContactIdentityIdOrNull(
    dpsContactIdentityId: Long = 123456,
    mapping: PersonIdentifierMappingDto? = PersonIdentifierMappingDto(
      nomisPersonId = 654321,
      nomisSequenceNumber = 4,
      dpsId = dpsContactIdentityId.toString(),
      mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/identifier/dps-contact-identifier-id/$dpsContactIdentityId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/identifier/dps-contact-identifier-id/$dpsContactIdentityId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreateIdentifierMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/identifier").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateIdentifierMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/identifier").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateIdentifierMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/identifier")
        .inScenario("Retry Mapping Identifier Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Identifier Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/identifier")
        .inScenario("Retry Mapping Identifier Scenario")
        .whenScenarioStateIs("Cause Mapping Identifier Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsPrisonerContactRestrictionIdOrNull(
    dpsPrisonerContactRestrictionId: Long = 123456,
    mapping: PersonContactRestrictionMappingDto? = PersonContactRestrictionMappingDto(
      nomisId = 654321,
      dpsId = dpsPrisonerContactRestrictionId.toString(),
      mappingType = PersonContactRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/$dpsPrisonerContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/$dpsPrisonerContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByDpsPrisonerContactRestrictionId(
    dpsPrisonerContactRestrictionId: Long = 123456,
    mapping: PersonContactRestrictionMappingDto = PersonContactRestrictionMappingDto(
      nomisId = 654321,
      dpsId = dpsPrisonerContactRestrictionId.toString(),
      mappingType = PersonContactRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId, mapping)

  fun stubCreateContactRestrictionMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/contact-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateContactRestrictionMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/contact-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateContactRestrictionMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/contact-restriction")
        .inScenario("Retry Mapping Contact Restriction Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Contact Restriction Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/contact-restriction")
        .inScenario("Retry Mapping Contact Restriction Scenario")
        .whenScenarioStateIs("Cause Mapping Contact Restriction Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByDpsContactRestrictionIdOrNull(
    dpsContactRestrictionId: Long = 123456,
    mapping: PersonRestrictionMappingDto? = PersonRestrictionMappingDto(
      nomisId = 654321,
      dpsId = dpsContactRestrictionId.toString(),
      mappingType = PersonRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/person-restriction/dps-contact-restriction-id/$dpsContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/person-restriction/dps-contact-restriction-id/$dpsContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactRestrictionId(
    dpsContactRestrictionId: Long = 123456,
    mapping: PersonRestrictionMappingDto = PersonRestrictionMappingDto(
      nomisId = 654321,
      dpsId = dpsContactRestrictionId.toString(),
      mappingType = PersonRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactRestrictionIdOrNull(dpsContactRestrictionId, mapping)

  fun stubCreatePersonRestrictionMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/person-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePersonRestrictionMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/person-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePersonRestrictionMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/person-restriction")
        .inScenario("Retry Mapping Person Restriction Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Person Restriction Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/person-restriction")
        .inScenario("Retry Mapping Person Restriction Scenario")
        .whenScenarioStateIs("Cause Mapping Person Restriction Success")
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
