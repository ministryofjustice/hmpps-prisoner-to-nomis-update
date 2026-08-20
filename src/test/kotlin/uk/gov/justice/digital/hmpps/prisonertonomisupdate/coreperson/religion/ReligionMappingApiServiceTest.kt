package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(ReligionMappingApiService::class, ReligionMappingApiMockServer::class, RetryApiService::class)
class ReligionMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ReligionMappingApiService

  @Autowired
  private lateinit var mockServer: ReligionMappingApiMockServer

  @Nested
  inner class GetByCprIds {
    private val cprReligionIds = listOf(
      "802dfae7-45f0-4c22-b369-bfe7da5e54e2",
      "9bc3e4f5-0c11-4aa7-a3b8-2d6cc7f4e1a1",
    )

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetReligionMappings(
        mappings = cprReligionIds.mapIndexed { index, cprReligionId ->
          ReligionMappingDto(
            cprId = cprReligionId,
            nomisId = 10000L + index,
            nomisPrisonNumber = "A1234AA",
            mappingType = ReligionMappingDto.MappingType.MIGRATED,
            label = null,
            whenCreated = null,
          )
        },
      )

      apiService.getByCprIds(cprReligionIds)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass CPR ids to service`() = runTest {
      mockServer.stubGetReligionMappings(
        mappings = cprReligionIds.mapIndexed { index, cprReligionId ->
          ReligionMappingDto(
            cprId = cprReligionId,
            nomisId = 10000L + index,
            nomisPrisonNumber = "A1234AA",
            mappingType = ReligionMappingDto.MappingType.MIGRATED,
            label = null,
            whenCreated = null,
          )
        },
      )

      apiService.getByCprIds(cprReligionIds)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion/cpr-ids"))
          .withQueryParam("ids", havingExactly(*cprReligionIds.toTypedArray())),
      )
    }

    @Test
    fun `will return mapping data`() = runTest {
      mockServer.stubGetReligionMappings(
        mappings = cprReligionIds.mapIndexed { index, cprReligionId ->
          ReligionMappingDto(
            cprId = cprReligionId,
            nomisId = 10000L + index,
            nomisPrisonNumber = "A1234AA",
            mappingType = ReligionMappingDto.MappingType.MIGRATED,
            label = null,
            whenCreated = null,
          )
        },
      )

      val mappings = apiService.getByCprIds(cprReligionIds)

      assertThat(mappings).hasSize(2)
      assertThat(mappings[0].cprId).isEqualTo(cprReligionIds[0])
      assertThat(mappings[0].nomisId).isEqualTo(10000L)
      assertThat(mappings[0].nomisPrisonNumber).isEqualTo("A1234AA")
      assertThat(mappings[1].cprId).isEqualTo(cprReligionIds[1])
      assertThat(mappings[1].nomisId).isEqualTo(10001L)
    }
  }
}
