package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId

@SpringAPIServiceTest
@Import(OfficialVisitsDpsApiService::class, OfficialVisitsConfiguration::class)
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
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }
}
