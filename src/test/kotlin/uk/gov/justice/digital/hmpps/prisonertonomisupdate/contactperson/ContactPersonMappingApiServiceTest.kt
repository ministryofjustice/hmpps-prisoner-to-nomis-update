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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED

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
}
