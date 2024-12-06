package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonPhoneMappingDto

@SpringAPIServiceTest
@Import(ContactPersonMappingApiService::class, ContactPersonMappingApiMockServer::class)
class ContactPersonMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonMappingApiService

  @Autowired
  private lateinit var mockServer: ContactPersonMappingApiMockServer

  @Nested
  inner class GetByDpsContactIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactIdOrNull(dpsContactId = 1234567)

      apiService.getByDpsContactIdOrNull(dpsContactId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactIdOrNull()

      apiService.getByDpsContactIdOrNull(dpsContactId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/person/dps-contact-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsContactIdOrNull(
        dpsContactId = 1234567,
        mapping = PersonMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = PersonMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactIdOrNull(dpsContactId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsContactIdOrNull(
        dpsContactId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsContactIdOrNull(dpsContactId = 1234567))
    }
  }

  @Nested
  inner class CreatePersonMapping {
    @Test
    internal fun `will pass oath2 token to create person mapping endpoint`() = runTest {
      mockServer.stubCreatePersonMapping()

      apiService.createPersonMapping(
        PersonMappingDto(
          mappingType = DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/person"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreatePersonMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = DPS_CREATED,
            ),
            existing = PersonMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createPersonMapping(
          PersonMappingDto(
            mappingType = PersonMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }

  @Nested
  inner class GetByDpsPrisonerContactIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = 1234567)

      apiService.getByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsPrisonerContactIdOrNull()

      apiService.getByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/dps-prisoner-contact-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsPrisonerContactIdOrNull(
        dpsPrisonerContactId = 1234567,
        mapping = PersonContactMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = PersonContactMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsPrisonerContactIdOrNull(
        dpsPrisonerContactId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = 1234567))
    }
  }

  @Nested
  inner class GetByDpsPrisonerContactId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = 1234567)

      apiService.getByDpsPrisonerContactId(dpsPrisonerContactId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = 1234567)

      apiService.getByDpsPrisonerContactId(dpsPrisonerContactId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/dps-prisoner-contact-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByDpsPrisonerContactId(
        dpsPrisonerContactId = 1234567,
        mapping = PersonContactMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = PersonContactMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsPrisonerContactId(dpsPrisonerContactId = 1234567)

      assertThat(mapping.nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class CreateContactMapping {
    @Test
    internal fun `will pass oath2 token to create contact mapping endpoint`() = runTest {
      mockServer.stubCreateContactMapping()

      apiService.createContactMapping(
        PersonContactMappingDto(
          mappingType = PersonContactMappingDto.MappingType.DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreateContactMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonContactMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonContactMappingDto.MappingType.DPS_CREATED,
            ),
            existing = PersonContactMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = PersonContactMappingDto.MappingType.DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createContactMapping(
          PersonContactMappingDto(
            mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }

  @Nested
  inner class GetByDpsContactAddressIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = 1234567)

      apiService.getByDpsContactAddressIdOrNull(dpsContactAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactAddressIdOrNull()

      apiService.getByDpsContactAddressIdOrNull(dpsContactAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/address/dps-contact-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsContactAddressIdOrNull(
        dpsContactAddressId = 1234567,
        mapping = PersonAddressMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactAddressIdOrNull(dpsContactAddressId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsContactAddressIdOrNull(
        dpsContactAddressId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsContactAddressIdOrNull(dpsContactAddressId = 1234567))
    }
  }

  @Nested
  inner class GetByDpsContactAddressId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactAddressId(dpsContactAddressId = 1234567)

      apiService.getByDpsContactAddressId(dpsContactAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactAddressId(dpsContactAddressId = 1234567)

      apiService.getByDpsContactAddressId(dpsContactAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/address/dps-contact-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByDpsContactAddressId(
        dpsContactAddressId = 1234567,
        mapping = PersonAddressMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactAddressId(dpsContactAddressId = 1234567)

      assertThat(mapping.nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class CreateAddressMapping {
    @Test
    internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
      mockServer.stubCreateAddressMapping()

      apiService.createAddressMapping(
        PersonAddressMappingDto(
          mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/address"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreateAddressMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonAddressMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED,
            ),
            existing = PersonAddressMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createAddressMapping(
          PersonAddressMappingDto(
            mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }

  @Nested
  inner class GetByDpsContactEmailIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactEmailIdOrNull(dpsContactEmailId = 1234567)

      apiService.getByDpsContactEmailIdOrNull(dpsContactEmailId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactEmailIdOrNull()

      apiService.getByDpsContactEmailIdOrNull(dpsContactEmailId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/email/dps-contact-email-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsContactEmailIdOrNull(
        dpsContactEmailId = 1234567,
        mapping = PersonEmailMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactEmailIdOrNull(dpsContactEmailId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsContactEmailIdOrNull(
        dpsContactEmailId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsContactEmailIdOrNull(dpsContactEmailId = 1234567))
    }
  }

  @Nested
  inner class GetByDpsContactPhoneIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId = 1234567)

      apiService.getByDpsContactPhoneIdOrNull(dpsContactPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactPhoneIdOrNull()

      apiService.getByDpsContactPhoneIdOrNull(dpsContactPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/dps-contact-phone-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsContactPhoneIdOrNull(
        dpsContactPhoneId = 1234567,
        mapping = PersonPhoneMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
          mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactPhoneIdOrNull(dpsContactPhoneId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsContactPhoneIdOrNull(
        dpsContactPhoneId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsContactPhoneIdOrNull(dpsContactPhoneId = 1234567))
    }
  }

  @Nested
  inner class GetByDpsContactAddressPhoneIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = 1234567)

      apiService.getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactAddressPhoneIdOrNull()

      apiService.getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/dps-contact-address-phone-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsContactAddressPhoneIdOrNull(
        dpsContactAddressPhoneId = 1234567,
        mapping = PersonPhoneMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
          mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsContactAddressPhoneIdOrNull(
        dpsContactAddressPhoneId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = 1234567))
    }
  }

  @Nested
  inner class CreateEmailMapping {
    @Test
    internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
      mockServer.stubCreateEmailMapping()

      apiService.createEmailMapping(
        PersonEmailMappingDto(
          mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/email"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreateEmailMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonEmailMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
            ),
            existing = PersonEmailMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createEmailMapping(
          PersonEmailMappingDto(
            mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }

  @Nested
  inner class CreatePhoneMapping {
    @Test
    internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
      mockServer.stubCreatePhoneMapping()

      apiService.createPhoneMapping(
        PersonPhoneMappingDto(
          mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreatePhoneMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonPhoneMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
            ),
            existing = PersonPhoneMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createPhoneMapping(
          PersonPhoneMappingDto(
            mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
            dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }

  @Nested
  inner class CreateIdentifierMapping {
    @Test
    internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
      mockServer.stubCreateIdentifierMapping()

      apiService.createIdentifierMapping(
        PersonIdentifierMappingDto(
          mappingType = PersonIdentifierMappingDto.MappingType.DPS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisPersonId = 2234567890
      val nomisSequenceNumber = 4
      val dpsId = "2234567890"
      val existingNomisSequenceNumber = 5

      mockServer.stubCreateIdentifierMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonIdentifierMappingDto(
              dpsId = dpsId,
              nomisPersonId = nomisPersonId,
              nomisSequenceNumber = nomisSequenceNumber.toLong(),
              mappingType = PersonIdentifierMappingDto.MappingType.DPS_CREATED,
            ),
            existing = PersonIdentifierMappingDto(
              dpsId = dpsId,
              nomisPersonId = nomisPersonId,
              nomisSequenceNumber = existingNomisSequenceNumber.toLong(),
              mappingType = PersonIdentifierMappingDto.MappingType.DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createIdentifierMapping(
          PersonIdentifierMappingDto(
            mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
            nomisPersonId = nomisPersonId,
            nomisSequenceNumber = nomisSequenceNumber.toLong(),
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisPersonId"]).isEqualTo(nomisPersonId)
      assertThat(error.moreInfo.existing!!["nomisSequenceNumber"]).isEqualTo(existingNomisSequenceNumber)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisPersonId"]).isEqualTo(nomisPersonId)
      assertThat(error.moreInfo.duplicate["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber)
    }
  }

  @Nested
  inner class GetByDpsContactIdentifierIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId = 1234567)

      apiService.getByDpsContactIdentityIdOrNull(dpsContactIdentityId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId = 1234567)

      apiService.getByDpsContactIdentityIdOrNull(dpsContactIdentityId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/dps-contact-identifier-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsContactIdentityIdOrNull(
        dpsContactIdentityId = 1234567,
        mapping = PersonIdentifierMappingDto(
          dpsId = "1234567",
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsContactIdentityIdOrNull(dpsContactIdentityId = 1234567)

      assertThat(mapping?.nomisPersonId).isEqualTo(1234567)
      assertThat(mapping?.nomisSequenceNumber).isEqualTo(4)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsContactIdentityIdOrNull(
        dpsContactIdentityId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsContactIdentityIdOrNull(dpsContactIdentityId = 1234567))
    }
  }

  @Nested
  inner class GetByDpsPrisonerContactRestrictionIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = 1234567)

      apiService.getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetByDpsPrisonerContactRestrictionIdOrNull()

      apiService.getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsPrisonerContactRestrictionIdOrNull(
        dpsPrisonerContactRestrictionId = 1234567,
        mapping = PersonContactRestrictionMappingDto(
          dpsId = "1234567",
          nomisId = 6543232,
          mappingType = PersonContactRestrictionMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = 1234567)

      assertThat(mapping?.nomisId).isEqualTo(6543232)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsPrisonerContactRestrictionIdOrNull(
        dpsPrisonerContactRestrictionId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = 1234567))
    }
  }

  @Nested
  inner class CreateContactRestrictionMapping {
    @Test
    internal fun `will pass oath2 token to create contact restriction mapping endpoint`() = runTest {
      mockServer.stubCreateContactRestrictionMapping()

      apiService.createContactRestrictionMapping(
        PersonContactRestrictionMappingDto(
          mappingType = PersonContactRestrictionMappingDto.MappingType.DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreateContactRestrictionMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonContactRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonContactRestrictionMappingDto.MappingType.DPS_CREATED,
            ),
            existing = PersonContactRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = PersonContactRestrictionMappingDto.MappingType.DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createContactRestrictionMapping(
          PersonContactRestrictionMappingDto(
            mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }

  @Nested
  inner class CreatePersonRestrictionMapping {
    @Test
    internal fun `will pass oath2 token to create person restriction mapping endpoint`() = runTest {
      mockServer.stubCreatePersonRestrictionMapping()

      apiService.createPersonRestrictionMapping(
        PersonRestrictionMappingDto(
          mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      val nomisId = 2234567890
      val dpsId = "2234567890"
      val existingNomisId = 3234567890

      mockServer.stubCreatePersonRestrictionMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
            ),
            existing = PersonRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = existingNomisId,
              mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createPersonRestrictionMapping(
          PersonRestrictionMappingDto(
            mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
            nomisId = nomisId,
            dpsId = dpsId,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.existing!!["nomisId"]).isEqualTo(existingNomisId)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
    }
  }
}
