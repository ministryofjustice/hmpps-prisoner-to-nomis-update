package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceRecallMappingsUpdateDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DpsCourtCaseBatchMappingDto
import java.util.*

@SpringAPIServiceTest
@Import(CourtSentencingMappingService::class, CourtSentencingConfiguration::class, CourtSentencingMappingApiMockServer::class)
class CourtSentencingMappingServiceTest {
  @Autowired
  private lateinit var apiService: CourtSentencingMappingService

  @Autowired
  private lateinit var courtCaseMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Nested
  inner class ReplaceMappings {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtCaseMappingApiMockServer.stubReplaceMappings()

      apiService.replaceMappings(
        CourtCaseBatchMappingDto(
          mappingType = CourtCaseBatchMappingDto.MappingType.NOMIS_CREATED,
          courtCases = emptyList(),
          courtAppearances = emptyList(),
          courtCharges = emptyList(),
          sentences = emptyList(),
          sentenceTerms = emptyList(),
          label = null,
          whenCreated = null,
        ),
      )

      courtCaseMappingApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/replace")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class DeleteMappingsByDpsIds {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtCaseMappingApiMockServer.stubDeleteMappingsByDpsIds()

      apiService.deleteMappingsByDpsIds(
        DpsCourtCaseBatchMappingDto(
          courtCases = emptyList(),
          courtAppearances = emptyList(),
          courtCharges = emptyList(),
          sentences = emptyList(),
          sentenceTerms = emptyList(),
        ),
      )

      courtCaseMappingApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/delete-by-dps-ids")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class UpdateAppearanceRecallMappings {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtCaseMappingApiMockServer.stubUpdateAppearanceRecallMapping("123")

      apiService.updateAppearanceRecallMappings(
        recallId = "123",
        CourtAppearanceRecallMappingsUpdateDto(
          nomisCourtAppearanceIds = emptyList(),
        ),
      )

      courtCaseMappingApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/recall/123")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class GetAllCourtAppearancesByNomisIds {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtCaseMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds()

      apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))

      courtCaseMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass requested IDs to service`() = runTest {
      courtCaseMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds()

      apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))

      courtCaseMappingApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-ids/get-list")).withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withRequestBody(equalToJson("[123, 456]")),
      )
    }

    @Test
    internal fun `will parse response`() = runTest {
      val dpsId1 = UUID.randomUUID().toString()
      val dpsId2 = UUID.randomUUID().toString()
      courtCaseMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds(
        dpsCourtAppearanceIds = listOf(dpsId1, dpsId2),
      )

      with(apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))) {
        assertThat(this).hasSize(2)
        assertThat(this[0].dpsCourtAppearanceId).isEqualTo(dpsId1)
        assertThat(this[1].dpsCourtAppearanceId).isEqualTo(dpsId2)
      }
    }

    @Test
    internal fun `will throw if error`() = runTest {
      courtCaseMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))
      }
    }
  }
}
