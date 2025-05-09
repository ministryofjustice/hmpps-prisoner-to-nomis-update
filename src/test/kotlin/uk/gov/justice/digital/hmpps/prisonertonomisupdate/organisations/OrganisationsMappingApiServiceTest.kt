package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
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
}
