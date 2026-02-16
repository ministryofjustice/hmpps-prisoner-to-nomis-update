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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@SpringAPIServiceTest
@Import(OfficialVisitsNomisApiService::class, OfficialVisitsConfiguration::class, OfficialVisitsNomisApiMockServer::class, RetryApiService::class)
class OfficialVisitsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: OfficialVisitsNomisApiService

  @Autowired
  private lateinit var mockServer: OfficialVisitsNomisApiMockServer

  @Nested
  inner class GetOfficialVisitIds {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisitIds(
        pageNumber = 0,
        pageSize = 20,
        content = listOf(
          VisitIdResponse(
            visitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIds(
        pageNumber = 0,
        pageSize = 20,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetOfficialVisitIds(
        pageNumber = 10,
        pageSize = 30,
        content = listOf(
          VisitIdResponse(
            visitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIds(
        pageNumber = 10,
        pageSize = 30,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/ids"))
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }

  @Nested
  inner class GetOfficialVisit {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisit(
        visitId = 1234,
      )

      apiService.getOfficialVisit(
        visitId = 1234,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get visit endpoint`() = runTest {
      mockServer.stubGetOfficialVisit(
        visitId = 1234,
      )

      apiService.getOfficialVisit(
        visitId = 1234,
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/1234")),
      )
    }
  }

  @Nested
  inner class GetOfficialVisitOrNull {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisitOrNull(
        visitId = 1234,
      )

      apiService.getOfficialVisitOrNull(
        visitId = 1234,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get time slot endpoint`() = runTest {
      mockServer.stubGetOfficialVisitOrNull(
        visitId = 1234,
      )

      apiService.getOfficialVisitOrNull(
        visitId = 1234,
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/1234")),
      )
    }

    @Test
    fun `will return null when not found`() = runTest {
      mockServer.stubGetOfficialVisitOrNull(
        visitId = 1234,
        response = null,
      )

      assertThat(
        apiService.getOfficialVisitOrNull(
          visitId = 1234,
        ),
      ).isNull()
    }

    @Test
    fun `will return mapping when  found`() = runTest {
      mockServer.stubGetOfficialVisitOrNull(
        visitId = 1234,
        response = officialVisitResponse(),
      )

      assertThat(
        apiService.getOfficialVisitOrNull(
          visitId = 1234,
        ),
      ).isNotNull()
    }
  }

  @Nested
  inner class GetOfficialVisitIdsByLastId {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisitIdsByLastId(
        content = listOf(
          VisitIdResponse(
            visitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIdsByLastId(
        lastVisitId = 0,
        pageSize = 20,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetOfficialVisitIdsByLastId(
        visitId = 99,
        content = listOf(
          VisitIdResponse(
            visitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIdsByLastId(
        lastVisitId = 99,
        pageSize = 30,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/ids/all-from-id"))
          .withQueryParam("visitId", equalTo("99"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }

  @Nested
  inner class GetOfficialVisitsForPrisoner {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get visits endpoint`() = runTest {
      mockServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoner/A1234KT/official-visits")),
      )
    }

    @Test
    fun `will call the get visits endpoint with parameters`() = runTest {
      mockServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoner/A1234KT/official-visits"))
          .withQueryParam("fromDate", equalTo("2020-01-01"))
          .withQueryParam("toDate", equalTo("2020-01-02")),
      )
    }
  }
}
