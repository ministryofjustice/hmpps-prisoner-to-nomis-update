package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmploymentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import java.util.UUID

@Component
class ContactPersonMappingApiMockServer(private val jsonMapper: JsonMapper) {

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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/dps-contact-id/$dpsContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactId(
    dpsContactId: Long = 123456,
    mapping: PersonMappingDto = PersonMappingDto(
      nomisId = dpsContactId,
      dpsId = dpsContactId.toString(),
      mappingType = PersonMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactIdOrNull(dpsContactId, mapping)

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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact/dps-prisoner-contact-id/$dpsPrisonerContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/dps-contact-address-id/$dpsContactAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactAddressIdOrNullAsNullFollowedByValue(
    dpsContactAddressId: Long = 123456,
    mapping: PersonAddressMappingDto = PersonAddressMappingDto(
      nomisId = 654321,
      dpsId = dpsContactAddressId.toString(),
      mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
    ),
  ) {
    val scenario = "Address Mapping Created Scenario"
    val foundScenario = "Address Mapping Found Scenario"
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/contact-person/address/dps-contact-address-id/$dpsContactAddressId"))
        .inScenario(scenario)
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ).willSetStateTo(foundScenario),
    )

    mappingServer.stubFor(
      get(urlEqualTo("/mapping/contact-person/address/dps-contact-address-id/$dpsContactAddressId"))
        .inScenario(scenario)
        .whenScenarioStateIs(foundScenario)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
  fun stubGetByDpsContactAddressId(
    dpsContactAddressId: Long = 123456,
    mapping: PersonAddressMappingDto = PersonAddressMappingDto(
      nomisId = 654321,
      dpsId = dpsContactAddressId.toString(),
      mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactAddressIdOrNull(dpsContactAddressId, mapping)

  fun stubDeleteByNomisAddressId(
    nomisAddressId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/email/dps-contact-email-id/$dpsContactEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactEmailId(
    dpsContactEmailId: Long = 123456,
    mapping: PersonEmailMappingDto = PersonEmailMappingDto(
      nomisId = 654321,
      dpsId = dpsContactEmailId.toString(),
      mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactEmailIdOrNull(dpsContactEmailId, mapping)

  fun stubDeleteByNomisEmailId(
    nomisInternetAddressId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/dps-contact-phone-id/$dpsContactPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactPhoneId(
    dpsContactPhoneId: Long = 123456,
    mapping: PersonPhoneMappingDto = PersonPhoneMappingDto(
      nomisId = 654321,
      dpsId = dpsContactPhoneId.toString(),
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
      mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId, mapping)

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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/dps-contact-address-phone-id/$dpsContactAddressPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByDpsContactAddressPhoneId(
    dpsContactAddressPhoneId: Long = 123456,
    mapping: PersonPhoneMappingDto = PersonPhoneMappingDto(
      nomisId = 654321,
      dpsId = dpsContactAddressPhoneId.toString(),
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
      mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId, mapping)

  fun stubDeleteByNomisPhoneId(
    nomisPhoneId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/identifier/dps-contact-identifier-id/$dpsContactIdentityId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByDpsContactIdentityId(
    dpsContactIdentityId: Long = 123456,
    mapping: PersonIdentifierMappingDto = PersonIdentifierMappingDto(
      nomisPersonId = 654321,
      nomisSequenceNumber = 4,
      dpsId = dpsContactIdentityId.toString(),
      mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId, mapping)

  fun stubDeleteByNomisIdentifierIds(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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

  fun stubGetByDpsContactEmploymentIdOrNull(
    dpsContactEmploymentId: Long = 123456,
    mapping: PersonEmploymentMappingDto? = PersonEmploymentMappingDto(
      nomisPersonId = 654321,
      nomisSequenceNumber = 4,
      dpsId = dpsContactEmploymentId.toString(),
      mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/employment/dps-contact-employment-id/$dpsContactEmploymentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/employment/dps-contact-employment-id/$dpsContactEmploymentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByDpsContactEmploymentId(
    dpsContactEmploymentId: Long = 123456,
    mapping: PersonEmploymentMappingDto = PersonEmploymentMappingDto(
      nomisPersonId = 654321,
      nomisSequenceNumber = 4,
      dpsId = dpsContactEmploymentId.toString(),
      mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsContactEmploymentIdOrNull(dpsContactEmploymentId, mapping)

  fun stubDeleteByNomisEmploymentIds(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/contact-person/employment/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateEmploymentMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/employment").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateEmploymentMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/employment").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateEmploymentMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/employment")
        .inScenario("Retry Mapping Employment Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Employment Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/employment")
        .inScenario("Retry Mapping Employment Scenario")
        .whenScenarioStateIs("Cause Mapping Employment Success")
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/$dpsPrisonerContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/person-restriction/dps-contact-restriction-id/$dpsContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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

  fun stubGetByNomisPrisonerRestrictionIdOrNull(
    nomisRestrictionId: Long = 123456,
    dpsRestrictionId: String = UUID.randomUUID().toString(),
    mapping: PrisonerRestrictionMappingDto? = PrisonerRestrictionMappingDto(
      offenderNo = "A1234KT",
      nomisId = nomisRestrictionId,
      dpsId = dpsRestrictionId,
      mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreatePrisonerRestrictionMapping() {
    mappingServer.stubFor(
      post("/mapping/contact-person/prisoner-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePrisonerRestrictionMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/contact-person/prisoner-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePrisonerRestrictionMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/contact-person/prisoner-restriction")
        .inScenario("Retry Mapping Prisoner Restriction Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Prisoner Restriction Success"),
    )

    mappingServer.stubFor(
      post("/mapping/contact-person/prisoner-restriction")
        .inScenario("Retry Mapping Prisoner Restriction Scenario")
        .whenScenarioStateIs("Cause Mapping Prisoner Restriction Success")
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

  fun stubGetByDpsPrisonerRestrictionIdOrNull(
    dpsPrisonerRestrictionId: Long = 123456,
    mapping: PrisonerRestrictionMappingDto? = PrisonerRestrictionMappingDto(
      offenderNo = "A1234KT",
      nomisId = 654321,
      dpsId = dpsPrisonerRestrictionId.toString(),
      mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/dps-prisoner-restriction-id/$dpsPrisonerRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/dps-prisoner-restriction-id/$dpsPrisonerRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByDpsPrisonerRestrictionId(
    dpsPrisonerRestrictionId: Long = 123456,
    mapping: PrisonerRestrictionMappingDto = PrisonerRestrictionMappingDto(
      offenderNo = "A1234KT",
      nomisId = 654321,
      dpsId = dpsPrisonerRestrictionId.toString(),
      mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByDpsPrisonerRestrictionIdOrNull(dpsPrisonerRestrictionId, mapping)

  fun stubDeleteByDpsPrisonerRestrictionId(
    dpsPrisonerRestrictionId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/contact-person/prisoner-restriction/dps-prisoner-restriction-id/$dpsPrisonerRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }
}
