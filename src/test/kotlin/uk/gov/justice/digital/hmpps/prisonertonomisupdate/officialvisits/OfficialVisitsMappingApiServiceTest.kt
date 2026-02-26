package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(OfficialVisitsMappingService::class, OfficialVisitsMappingApiMockServer::class, RetryApiService::class)
class OfficialVisitsMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: OfficialVisitsMappingService

  @Autowired
  private lateinit var mockServer: OfficialVisitsMappingApiMockServer

  @Nested
  inner class GetVisitByNomisIdsOrNull {
    val nomisVisitId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitByNomisIdOrNull(
        nomisVisitId = nomisVisitId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitByNomisIdOrNull(
        nomisVisitId = nomisVisitId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetVisitByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getVisitByNomisIdOrNull(
        nomisVisitId = nomisVisitId,
      )

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetVisitByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = null,
      )

      assertThat(
        apiService.getVisitByNomisIdOrNull(
          nomisVisitId = nomisVisitId,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class GetVisitByDpsIdsOrNull {
    val dpsVisitId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = dpsVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitByDpsIdOrNull(
        dpsVisitId = dpsVisitId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = dpsVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitByDpsIdOrNull(
        dpsVisitId = dpsVisitId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/dps-id/$dpsVisitId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = dpsVisitId.toString(),
          nomisId = 1234,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getVisitByDpsIdOrNull(
        dpsVisitId = dpsVisitId,
      )

      assertThat(mapping?.nomisId).isEqualTo(1234)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = null,
      )

      assertThat(
        apiService.getVisitByDpsIdOrNull(
          dpsVisitId = dpsVisitId,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class GetVisitorByDpsIdsOrNull {
    val dpsVisitorId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
        mapping = OfficialVisitorMappingDto(
          dpsId = "1234",
          nomisId = dpsVisitorId,
          mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
        mapping = OfficialVisitorMappingDto(
          dpsId = "1234",
          nomisId = dpsVisitorId,
          mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor/dps-id/$dpsVisitorId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
        mapping = OfficialVisitorMappingDto(
          dpsId = dpsVisitorId.toString(),
          nomisId = 1234,
          mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
      )

      assertThat(mapping?.nomisId).isEqualTo(1234)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetVisitorByDpsIdsOrNull(
        dpsVisitorId = dpsVisitorId,
        mapping = null,
      )

      assertThat(
        apiService.getVisitorByDpsIdsOrNull(
          dpsVisitorId = dpsVisitorId,
        ),
      ).isNull()
    }
  }
}
