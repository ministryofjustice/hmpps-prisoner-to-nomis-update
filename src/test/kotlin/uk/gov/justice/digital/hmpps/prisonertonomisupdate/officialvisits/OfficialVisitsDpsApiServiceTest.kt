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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(OfficialVisitsDpsApiService::class, OfficialVisitsConfiguration::class, RetryApiService::class)
class OfficialVisitsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: OfficialVisitsDpsApiService

  @Nested
  inner class GetOfficialVisitIds {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisitIds(
        pageNumber = 0,
        pageSize = 20,
        content = listOf(
          SyncOfficialVisitId(
            officialVisitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIds(
        pageNumber = 0,
        pageSize = 20,
      )

      dpsOfficialVisitsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisitIds(
        pageNumber = 10,
        pageSize = 30,
        content = listOf(
          SyncOfficialVisitId(
            officialVisitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIds(
        pageNumber = 10,
        pageSize = 30,
      )

      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/official-visits/identifiers"))
          .withQueryParam("currentTermOnly", equalTo("false"))
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }

  @Nested
  inner class GetOfficialVisit {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisit(officialVisitId = 1234)

      apiService.getOfficialVisitOrNull(visitId = 1234)

      dpsOfficialVisitsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisit(officialVisitId = 1234)

      apiService.getOfficialVisitOrNull(visitId = 1234)

      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/official-visit/id/1234")),
      )
    }

    @Test
    fun `will return null when not found`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisit(officialVisitId = 1234, response = null)

      assertThat(apiService.getOfficialVisitOrNull(visitId = 1234)).isNull()
    }

    @Test
    fun `will return visit when found`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisit(officialVisitId = 1234)

      assertThat(apiService.getOfficialVisitOrNull(visitId = 1234)).isNotNull()
    }
  }
}
