package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper
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

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  @Nested
  inner class GetByCprIds {
    private val cprReligionIds = listOf(
      "802dfae7-45f0-4c22-b369-bfe7da5e54e2",
      "9bc3e4f5-0c11-4aa7-a3b8-2d6cc7f4e1a1",
    )

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

  @Nested
  inner class GetReligionByCprIdOrNull {
    private val cprReligionId = "802dfae7-45f0-4c22-b369-bfe7da5e54e2"

    @Test
    fun `will request a mapping by CPR id`() = runTest {
      mockServer.stubGetReligionMapping(cprReligionId)

      apiService.getReligionByCprIdOrNull(cprReligionId)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion/cpr-id/$cprReligionId")),
      )
    }

    @Test
    fun `will return the mapping`() = runTest {
      mockServer.stubGetReligionMapping(cprReligionId)

      val mapping = apiService.getReligionByCprIdOrNull(cprReligionId)

      assertThat(mapping?.cprId).isEqualTo(cprReligionId)
      assertThat(mapping?.nomisId).isEqualTo(12345L)
      assertThat(mapping?.nomisPrisonNumber).isEqualTo("A1234AA")
    }

    @Test
    fun `will return null when the mapping does not exist`() = runTest {
      mockServer.stubGetReligionMapping(cprReligionId, mapping = null)

      assertThat(apiService.getReligionByCprIdOrNull(cprReligionId)).isNull()
    }
  }

  @Nested
  inner class CreateReligionMapping {
    private val mapping = ReligionMappingDto(
      cprId = "802dfae7-45f0-4c22-b369-bfe7da5e54e2",
      nomisId = 12345L,
      nomisPrisonNumber = "A1234AA",
      mappingType = ReligionMappingDto.MappingType.DPS_CREATED,
    )

    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateReligionMapping()

      apiService.createReligionMapping(mapping)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion"))
          .withRequestBody(equalToJson(jsonMapper.writeValueAsString(mapping))),
      )
    }
  }
}
