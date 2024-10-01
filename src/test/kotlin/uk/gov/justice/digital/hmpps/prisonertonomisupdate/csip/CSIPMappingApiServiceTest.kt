package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPMappingApiService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
class CSIPMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: CSIPMappingApiService

  @Autowired
  private lateinit var csipMappingApiMockServer: CSIPMappingApiMockServer

  @Nested
  inner class GetByDpsId {
    private val dpsCSIPReportId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipMappingApiMockServer.stubGetByDpsReportId(dpsCSIPReportId)

      apiService.getOrNullByDpsId(dpsCSIPReportId)

      csipMappingApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      csipMappingApiMockServer.stubGetByDpsReportId(dpsCSIPReportId)

      apiService.getOrNullByDpsId(dpsCSIPReportId)

      csipMappingApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")),
      )
    }

    @Test
    fun `will return dpsCSIPId when mapping exists`() = runTest {
      csipMappingApiMockServer.stubGetByDpsReportId(
        dpsCSIPReportId = dpsCSIPReportId,
        mapping = CSIPFullMappingDto(
          dpsCSIPReportId = dpsCSIPReportId,
          nomisCSIPReportId = 123456,
          mappingType = CSIPFullMappingDto.MappingType.MIGRATED,
          attendeeMappings = listOf(),
          factorMappings = listOf(),
          interviewMappings = listOf(),
          planMappings = listOf(),
          reviewMappings = listOf(),
        ),
      )

      val mapping = apiService.getOrNullByDpsId(dpsCSIPReportId)

      assertThat(mapping?.nomisCSIPReportId).isEqualTo(123456)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      csipMappingApiMockServer.stubGetByDpsReportId(NOT_FOUND)

      assertThat(apiService.getOrNullByDpsId(dpsCSIPReportId)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      csipMappingApiMockServer.stubGetByDpsReportId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOrNullByDpsId(dpsCSIPReportId)
      }
    }
  }

  @Nested
  inner class PostMapping {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        CSIPFullMappingDto(
          nomisCSIPReportId = 123456,
          dpsCSIPReportId = UUID.randomUUID().toString(),
          mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
          attendeeMappings = listOf(),
          factorMappings = listOf(),
          interviewMappings = listOf(),
          planMappings = listOf(),
          reviewMappings = listOf(),
        ),
      )

      csipMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsCSIPId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      csipMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        CSIPFullMappingDto(
          nomisCSIPReportId = 123456,
          dpsCSIPReportId = dpsCSIPId,
          mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
          attendeeMappings = listOf(),
          factorMappings = listOf(),
          interviewMappings = listOf(),
          planMappings = listOf(),
          reviewMappings = listOf(),
        ),
      )

      csipMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisCSIPReportId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("dpsCSIPReportId", equalTo(dpsCSIPId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }

  @Nested
  inner class DeleteMapping {
    private val dpsCSIPId = UUID.randomUUID().toString()

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      csipMappingApiMockServer.stubDeleteByDpsId(dpsCSIPId)

      apiService.deleteByDpsId(dpsCSIPId)

      csipMappingApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsCSIPId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      csipMappingApiMockServer.stubDeleteByDpsId(dpsCSIPId)

      apiService.deleteByDpsId(dpsCSIPId)

      csipMappingApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/all")),
      )
    }
  }
}
