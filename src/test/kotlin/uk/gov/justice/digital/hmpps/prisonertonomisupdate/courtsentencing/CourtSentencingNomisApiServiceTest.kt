package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingNomisApiMockServer.Companion.courtCaseRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(CourtSentencingNomisApiService::class, CourtSentencingNomisApiMockServer::class, RetryApiService::class)
class CourtSentencingNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSentencingNomisApiService

  @Autowired
  private lateinit var mockServer: CourtSentencingNomisApiMockServer

  @Nested
  inner class CloneCourtCase {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCloneCourtCase(offenderNo = "A1234KT", courtCaseId = 123)

      apiService.cloneCourtCase(offenderNo = "A1234KT", courtCaseId = 123)

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCloneCourtCase(offenderNo = "A1234KT", courtCaseId = 123)

      apiService.cloneCourtCase(offenderNo = "A1234KT", courtCaseId = 123)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/sentencing/court-cases/clone/123")),
      )
    }
  }

  @Nested
  inner class RepairCourtCase {

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubRepairCourtCase(offenderNo = "A1234KT", courtCaseId = 123)

      apiService.repairCourtCase(
        offenderNo = "A1234KT",
        courtCaseId = 123,
        courtCase = courtCaseRepairRequest(),
      )

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call repair endpoint`() = runTest {
      mockServer.stubRepairCourtCase(offenderNo = "A1234KT", courtCaseId = 123)

      apiService.repairCourtCase(
        offenderNo = "A1234KT",
        courtCaseId = 123,
        courtCase = courtCaseRepairRequest(),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/sentencing/court-cases/123/repair")),
      )
    }
  }
}
