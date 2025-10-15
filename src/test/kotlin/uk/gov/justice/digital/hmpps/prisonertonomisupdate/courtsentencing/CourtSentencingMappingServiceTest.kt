package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DpsCourtCaseBatchMappingDto

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
}
