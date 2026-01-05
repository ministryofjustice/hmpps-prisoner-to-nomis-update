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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(OfficialVisitsMappingService::class, OfficialVisitsMappingApiMockServer::class, RetryApiService::class)
class OfficialVisitsMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: OfficialVisitsMappingService

  @Autowired
  private lateinit var mockServer: OfficialVisitsMappingApiMockServer

  @Nested
  inner class GetByNomisIdsOrNull {
    val nomisVisitId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
      )

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = null,
      )

      assertThat(
        apiService.getByNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class GetByDpsIdsOrNull {
    val dpsVisitId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = dpsVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = dpsVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/dps-id/$dpsVisitId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = dpsVisitId.toString(),
          nomisId = 1234,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
      )

      assertThat(mapping?.nomisId).isEqualTo(1234)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByDpsIdsOrNull(
        dpsVisitId = dpsVisitId,
        mapping = null,
      )

      assertThat(
        apiService.getByDpsIdsOrNull(
          dpsVisitId = dpsVisitId,
        ),
      ).isNull()
    }
  }
}
