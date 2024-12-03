package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import java.util.UUID

@SpringAPIServiceTest
@Import(CaseNotesMappingApiService::class, CaseNotesMappingApiMockServer::class)
class CaseNotesMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: CaseNotesMappingApiService

  @Autowired
  private lateinit var casenotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Nested
  inner class GetByDpsId {
    private val dpsCaseNoteId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      casenotesMappingApiMockServer.stubGetByDpsId(dpsCaseNoteId)

      apiService.getOrNullByDpsId(dpsCaseNoteId)

      casenotesMappingApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      casenotesMappingApiMockServer.stubGetByDpsId(dpsCaseNoteId)

      apiService.getOrNullByDpsId(dpsCaseNoteId)

      casenotesMappingApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/casenotes/dps-casenote-id/$dpsCaseNoteId/all")),
      )
    }

    @Test
    fun `will return dpsCaseNoteId when mapping exists`() = runTest {
      casenotesMappingApiMockServer.stubGetByDpsId(
        dpsCaseNoteId = dpsCaseNoteId,
        mapping = CaseNoteMappingDto(
          dpsCaseNoteId = dpsCaseNoteId,
          nomisBookingId = 123456,
          offenderNo = "A1234AA",
          nomisCaseNoteId = 1,
          mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getOrNullByDpsId(dpsCaseNoteId)?.firstOrNull()

      assertThat(mapping?.nomisBookingId).isEqualTo(123456)
      assertThat(mapping?.nomisCaseNoteId).isEqualTo(1)
      assertThat(mapping?.dpsCaseNoteId).isEqualTo(dpsCaseNoteId)
      assertThat(mapping?.offenderNo).isEqualTo("A1234AA")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      casenotesMappingApiMockServer.stubGetByDpsId(NOT_FOUND)

      assertThat(apiService.getOrNullByDpsId(dpsCaseNoteId)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      casenotesMappingApiMockServer.stubGetByDpsId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOrNullByDpsId(dpsCaseNoteId)
      }
    }
  }

  @Nested
  inner class PostMapping {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      casenotesMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        CaseNoteMappingDto(
          nomisBookingId = 123456,
          offenderNo = "A1234AA",
          nomisCaseNoteId = 1,
          dpsCaseNoteId = UUID.randomUUID().toString(),
          mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
        ),
      )

      casenotesMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass ids to service`() = runTest {
      val dpsCaseNoteId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      casenotesMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        CaseNoteMappingDto(
          nomisBookingId = 123456,
          offenderNo = "A1234AA",
          nomisCaseNoteId = 1,
          dpsCaseNoteId = dpsCaseNoteId,
          mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
        ),
      )

      casenotesMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("offenderNo", equalTo("A1234AA")))
          .withRequestBody(matchingJsonPath("nomisCaseNoteId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsCaseNoteId", equalTo(dpsCaseNoteId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }

  @Nested
  inner class DeleteMapping {
    private val dpsCaseNoteId = UUID.randomUUID().toString()

    @Test
    fun `will pass oath2 token to service`() = runTest {
      casenotesMappingApiMockServer.stubDeleteByDpsId(dpsCaseNoteId)

      apiService.deleteByDpsId(dpsCaseNoteId)

      casenotesMappingApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass ids to service`() = runTest {
      val dpsCaseNoteId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      casenotesMappingApiMockServer.stubDeleteByDpsId(dpsCaseNoteId)

      apiService.deleteByDpsId(dpsCaseNoteId)

      casenotesMappingApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/casenotes/dps-casenote-id/$dpsCaseNoteId")),
      )
    }
  }
}
