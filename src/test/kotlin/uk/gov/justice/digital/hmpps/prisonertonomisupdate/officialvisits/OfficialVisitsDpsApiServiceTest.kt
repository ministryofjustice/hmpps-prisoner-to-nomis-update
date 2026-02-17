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
import java.time.LocalDate

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

  @Nested
  inner class GetOfficialVisitsForPrisoner {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      dpsOfficialVisitsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get visits endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )
      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/prisoner/A1234KT")),
      )
    }

    @Test
    fun `will call the get visits endpoint with parameters`() = runTest {
      dpsOfficialVisitsServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )
      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/prisoner/A1234KT"))
          .withQueryParam("currentTermOnly", equalTo("false"))
          .withQueryParam("fromDate", equalTo("2020-01-01"))
          .withQueryParam("toDate", equalTo("2020-01-02")),
      )
    }
  }

  @Nested
  inner class GetTimeSlotsForPrison {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetTimeSlotsForPrison(prisonId = "BXI")

      apiService.getTimeSlotsForPrison(prisonId = "BXI")

      dpsOfficialVisitsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get slots endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetTimeSlotsForPrison(prisonId = "BXI")

      apiService.getTimeSlotsForPrison(prisonId = "BXI")

      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/time-slots/prison/BXI")),
      )
    }

    @Test
    fun `will call the get slots endpoint with parameters`() = runTest {
      dpsOfficialVisitsServer.stubGetTimeSlotsForPrison(prisonId = "BXI")

      apiService.getTimeSlotsForPrison(prisonId = "BXI", activeOnly = true)

      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/time-slots/prison/BXI"))
          .withQueryParam("activeOnly", equalTo("true")),
      )
    }
  }

  @Nested
  inner class GetTimeSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetTimeSlot(prisonTimeSlotId = 1234L)

      apiService.getTimeSlot(1234)

      dpsOfficialVisitsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get slot endpoint`() = runTest {
      dpsOfficialVisitsServer.stubGetTimeSlot(prisonTimeSlotId = 1234L)

      apiService.getTimeSlot(1234)

      dpsOfficialVisitsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/time-slot/1234")),
      )
    }
  }
}
