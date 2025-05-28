package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto

@SpringAPIServiceTest
@Import(OrganisationsMappingApiService::class, OrganisationsConfiguration::class, OrganisationsMappingApiMockServer::class)
class OrganisationsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsMappingApiService

  @Autowired
  private lateinit var mockServer: OrganisationsMappingApiMockServer

  @Nested
  inner class GetByDpsOrganisationId {
    private val dpsOrganisationId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsOrganisationId(dpsOrganisationId)

      apiService.getByDpsOrganisationIdOrNull(dpsOrganisationId)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass Dps id to service`() = runTest {
      mockServer.stubGetByDpsOrganisationId(dpsOrganisationId)

      apiService.getByDpsOrganisationIdOrNull(dpsOrganisationId)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")),
      )
    }

    @Test
    fun `will return dpsOrganisationId when mapping exists`() = runTest {
      mockServer.stubGetByDpsOrganisationId(
        dpsOrganisationId = dpsOrganisationId,
        mapping = OrganisationsMappingDto(
          dpsId = dpsOrganisationId,
          nomisId = 123456,
          mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsOrganisationIdOrNull(dpsOrganisationId)

      assertThat(mapping?.nomisId).isEqualTo(123456)
      assertThat(mapping?.dpsId).isEqualTo(dpsOrganisationId)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsOrganisationId(NOT_FOUND)

      assertThat(apiService.getByDpsOrganisationIdOrNull(dpsOrganisationId)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mockServer.stubGetByDpsOrganisationId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getByDpsOrganisationIdOrNull(dpsOrganisationId)
      }
    }
  }

  @Nested
  inner class DeleteByDpsOrganisationId {
    private val dpsOrganisationId = "1234567"

    @Test
    internal fun `will pass oath2 token to delete organisation mapping endpoint`() = runTest {
      mockServer.stubDeleteByDpsOrganisationId(dpsOrganisationId = dpsOrganisationId)

      apiService.deleteByDpsOrganisationId(dpsOrganisationId)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class Addresses {

    @Nested
    inner class GetByDpsAddressIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsAddressIdOrNull(dpsAddressId = 1234567)

        apiService.getByDpsAddressIdOrNull(dpsAddressId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsAddressIdOrNull()

        apiService.getByDpsAddressIdOrNull(dpsAddressId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/address/dps-address-id/1234567")),
        )
      }

      @Test
      fun `will return nomisId when mapping exists`() = runTest {
        mockServer.stubGetByDpsAddressIdOrNull(
          dpsAddressId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsAddressIdOrNull(dpsAddressId = 1234567)

        assertThat(mapping?.nomisId).isEqualTo(1234567)
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetByDpsAddressIdOrNull(
          dpsAddressId = 1234567,
          mapping = null,
        )

        assertThat(apiService.getByDpsAddressIdOrNull(dpsAddressId = 1234567)).isNull()
      }
    }

    @Nested
    inner class GetByDpsAddressId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsAddressId(dpsAddressId = 1234567)

        apiService.getByDpsAddressId(dpsAddressId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsAddressId(dpsAddressId = 1234567)

        apiService.getByDpsAddressId(dpsAddressId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/address/dps-address-id/1234567")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByDpsAddressId(
          dpsAddressId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsAddressId(dpsAddressId = 1234567)

        assertThat(mapping.nomisId).isEqualTo(1234567)
      }
    }

    @Nested
    inner class CreateAddressMapping {
      @Test
      internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
        mockServer.stubCreateAddressMapping()

        apiService.createAddressMapping(
          OrganisationsMappingDto(
            mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            nomisId = 1234567,
            dpsId = "1234567",
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address"))
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
              duplicate = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = nomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = existingNomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val error = assertThrows<DuplicateMappingException> {
          apiService.createAddressMapping(
            OrganisationsMappingDto(
              mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              nomisId = nomisId,
              dpsId = dpsId,
            ),
          )
        }.error

        assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.existing["nomisId"]).isEqualTo(existingNomisId)
        assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
      }
    }

    @Nested
    inner class DeleteByNomisAddressId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteByNomisAddressId(nomisAddressId = 1234567)

        apiService.deleteByNomisAddressId(nomisAddressId = 1234567)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubDeleteByNomisAddressId(nomisAddressId = 1234567)

        apiService.deleteByNomisAddressId(nomisAddressId = 1234567)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/1234567")),
        )
      }
    }
  }

  @Nested
  inner class Phones {

    @Nested
    inner class GetByDpsPhoneIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsPhoneIdOrNull(dpsPhoneId = 1234567)

        apiService.getByDpsPhoneIdOrNull(dpsPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsPhoneIdOrNull()

        apiService.getByDpsPhoneIdOrNull(dpsPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/phone/dps-phone-id/1234567")),
        )
      }

      @Test
      fun `will return nomisId when mapping exists`() = runTest {
        mockServer.stubGetByDpsPhoneIdOrNull(
          dpsPhoneId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsPhoneIdOrNull(dpsPhoneId = 1234567)

        assertThat(mapping?.nomisId).isEqualTo(1234567)
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetByDpsPhoneIdOrNull(
          dpsPhoneId = 1234567,
          mapping = null,
        )

        assertThat(apiService.getByDpsPhoneIdOrNull(dpsPhoneId = 1234567)).isNull()
      }
    }

    @Nested
    inner class GetByDpsPhoneId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsPhoneId(dpsPhoneId = 1234567)

        apiService.getByDpsPhoneId(dpsPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsPhoneId(dpsPhoneId = 1234567)

        apiService.getByDpsPhoneId(dpsPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/phone/dps-phone-id/1234567")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByDpsPhoneId(
          dpsPhoneId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsPhoneId(dpsPhoneId = 1234567)

        assertThat(mapping.nomisId).isEqualTo(1234567)
      }
    }

    @Nested
    inner class CreatePhoneMapping {
      @Test
      internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
        mockServer.stubCreatePhoneMapping()

        apiService.createPhoneMapping(
          OrganisationsMappingDto(
            mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            nomisId = 1234567,
            dpsId = "1234567",
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/phone"))
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
              duplicate = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = nomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = existingNomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val error = assertThrows<DuplicateMappingException> {
          apiService.createPhoneMapping(
            OrganisationsMappingDto(
              mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              nomisId = nomisId,
              dpsId = dpsId,
            ),
          )
        }.error

        assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.existing["nomisId"]).isEqualTo(existingNomisId)
        assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
      }
    }

    @Nested
    inner class DeleteByNomisPhoneId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteByNomisPhoneId(nomisPhoneId = 1234567)

        apiService.deleteByNomisPhoneId(nomisPhoneId = 1234567)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubDeleteByNomisPhoneId(nomisPhoneId = 1234567)

        apiService.deleteByNomisPhoneId(nomisPhoneId = 1234567)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/1234567")),
        )
      }
    }
  }

  @Nested
  inner class Emails {

    @Nested
    inner class GetByDpsEmailIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsEmailIdOrNull(dpsEmailId = 1234567)

        apiService.getByDpsEmailIdOrNull(dpsEmailId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsEmailIdOrNull()

        apiService.getByDpsEmailIdOrNull(dpsEmailId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/email/dps-email-id/1234567")),
        )
      }

      @Test
      fun `will return nomisId when mapping exists`() = runTest {
        mockServer.stubGetByDpsEmailIdOrNull(
          dpsEmailId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsEmailIdOrNull(dpsEmailId = 1234567)

        assertThat(mapping?.nomisId).isEqualTo(1234567)
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetByDpsEmailIdOrNull(
          dpsEmailId = 1234567,
          mapping = null,
        )

        assertThat(apiService.getByDpsEmailIdOrNull(dpsEmailId = 1234567)).isNull()
      }
    }

    @Nested
    inner class GetByDpsEmailId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsEmailId(dpsEmailId = 1234567)

        apiService.getByDpsEmailId(dpsEmailId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsEmailId(dpsEmailId = 1234567)

        apiService.getByDpsEmailId(dpsEmailId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/email/dps-email-id/1234567")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByDpsEmailId(
          dpsEmailId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsEmailId(dpsEmailId = 1234567)

        assertThat(mapping.nomisId).isEqualTo(1234567)
      }
    }

    @Nested
    inner class CreateEmailMapping {
      @Test
      internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
        mockServer.stubCreateEmailMapping()

        apiService.createEmailMapping(
          OrganisationsMappingDto(
            mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            nomisId = 1234567,
            dpsId = "1234567",
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/email"))
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
              duplicate = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = nomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = existingNomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val error = assertThrows<DuplicateMappingException> {
          apiService.createEmailMapping(
            OrganisationsMappingDto(
              mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              nomisId = nomisId,
              dpsId = dpsId,
            ),
          )
        }.error

        assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.existing["nomisId"]).isEqualTo(existingNomisId)
        assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
      }
    }

    @Nested
    inner class DeleteByNomisEmailId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteByNomisEmailId(nomisEmailId = 1234567)

        apiService.deleteByNomisEmailId(nomisEmailId = 1234567)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubDeleteByNomisEmailId(nomisEmailId = 1234567)

        apiService.deleteByNomisEmailId(nomisEmailId = 1234567)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/1234567")),
        )
      }
    }
  }

  @Nested
  inner class Web {

    @Nested
    inner class GetByDpsWebIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsWebIdOrNull(dpsWebId = 1234567)

        apiService.getByDpsWebIdOrNull(dpsWebId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsWebIdOrNull()

        apiService.getByDpsWebIdOrNull(dpsWebId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/web/dps-web-id/1234567")),
        )
      }

      @Test
      fun `will return nomisId when mapping exists`() = runTest {
        mockServer.stubGetByDpsWebIdOrNull(
          dpsWebId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsWebIdOrNull(dpsWebId = 1234567)

        assertThat(mapping?.nomisId).isEqualTo(1234567)
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetByDpsWebIdOrNull(
          dpsWebId = 1234567,
          mapping = null,
        )

        assertThat(apiService.getByDpsWebIdOrNull(dpsWebId = 1234567)).isNull()
      }
    }

    @Nested
    inner class GetByDpsWebId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsWebId(dpsWebId = 1234567)

        apiService.getByDpsWebId(dpsWebId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsWebId(dpsWebId = 1234567)

        apiService.getByDpsWebId(dpsWebId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/web/dps-web-id/1234567")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByDpsWebId(
          dpsWebId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsWebId(dpsWebId = 1234567)

        assertThat(mapping.nomisId).isEqualTo(1234567)
      }
    }

    @Nested
    inner class CreateWebMapping {
      @Test
      internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
        mockServer.stubCreateWebMapping()

        apiService.createWebMapping(
          OrganisationsMappingDto(
            mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            nomisId = 1234567,
            dpsId = "1234567",
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/web"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will throw error when 409 conflict`() = runTest {
        val nomisId = 2234567890
        val dpsId = "2234567890"
        val existingNomisId = 3234567890

        mockServer.stubCreateWebMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = nomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = existingNomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val error = assertThrows<DuplicateMappingException> {
          apiService.createWebMapping(
            OrganisationsMappingDto(
              mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              nomisId = nomisId,
              dpsId = dpsId,
            ),
          )
        }.error

        assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.existing["nomisId"]).isEqualTo(existingNomisId)
        assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
      }
    }

    @Nested
    inner class DeleteByNomisWebId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteByNomisWebId(nomisWebId = 1234567)

        apiService.deleteByNomisWebId(nomisWebId = 1234567)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubDeleteByNomisWebId(nomisWebId = 1234567)

        apiService.deleteByNomisWebId(nomisWebId = 1234567)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/1234567")),
        )
      }
    }
  }

  @Nested
  inner class AddressPhones {

    @Nested
    inner class GetByDpsAddressPhoneIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsAddressPhoneIdOrNull(dpsAddressPhoneId = 1234567)

        apiService.getByDpsAddressPhoneIdOrNull(dpsAddressPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsAddressPhoneIdOrNull()

        apiService.getByDpsAddressPhoneIdOrNull(dpsAddressPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/dps-address-phone-id/1234567")),
        )
      }

      @Test
      fun `will return nomisId when mapping exists`() = runTest {
        mockServer.stubGetByDpsAddressPhoneIdOrNull(
          dpsAddressPhoneId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsAddressPhoneIdOrNull(dpsAddressPhoneId = 1234567)

        assertThat(mapping?.nomisId).isEqualTo(1234567)
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetByDpsAddressPhoneIdOrNull(
          dpsAddressPhoneId = 1234567,
          mapping = null,
        )

        assertThat(apiService.getByDpsAddressPhoneIdOrNull(dpsAddressPhoneId = 1234567)).isNull()
      }
    }

    @Nested
    inner class GetByDpsAddressPhoneId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsAddressPhoneId(dpsAddressPhoneId = 1234567)

        apiService.getByDpsAddressPhoneId(dpsAddressPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsAddressPhoneId(dpsAddressPhoneId = 1234567)

        apiService.getByDpsAddressPhoneId(dpsAddressPhoneId = 1234567)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/dps-address-phone-id/1234567")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByDpsAddressPhoneId(
          dpsAddressPhoneId = 1234567,
          mapping = OrganisationsMappingDto(
            dpsId = "1234567",
            nomisId = 1234567,
            mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsAddressPhoneId(dpsAddressPhoneId = 1234567)

        assertThat(mapping.nomisId).isEqualTo(1234567)
      }
    }

    @Nested
    inner class CreateAddressPhoneMapping {
      @Test
      internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
        mockServer.stubCreateAddressPhoneMapping()

        apiService.createAddressPhoneMapping(
          OrganisationsMappingDto(
            mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            nomisId = 1234567,
            dpsId = "1234567",
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will throw error when 409 conflict`() = runTest {
        val nomisId = 2234567890
        val dpsId = "2234567890"
        val existingNomisId = 3234567890

        mockServer.stubCreateAddressPhoneMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = nomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
              existing = OrganisationsMappingDto(
                dpsId = dpsId,
                nomisId = existingNomisId,
                mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val error = assertThrows<DuplicateMappingException> {
          apiService.createAddressPhoneMapping(
            OrganisationsMappingDto(
              mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              nomisId = nomisId,
              dpsId = dpsId,
            ),
          )
        }.error

        assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.existing["nomisId"]).isEqualTo(existingNomisId)
        assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
        assertThat(error.moreInfo.duplicate["nomisId"]).isEqualTo(nomisId)
      }
    }

    @Nested
    inner class DeleteByNomisAddressPhoneId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteByNomisAddressPhoneId(nomisAddressPhoneId = 1234567)

        apiService.deleteByNomisAddressPhoneId(nomisAddressPhoneId = 1234567)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubDeleteByNomisAddressPhoneId(nomisAddressPhoneId = 1234567)

        apiService.deleteByNomisAddressPhoneId(nomisAddressPhoneId = 1234567)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/nomis-phone-id/1234567")),
        )
      }
    }
  }
}
