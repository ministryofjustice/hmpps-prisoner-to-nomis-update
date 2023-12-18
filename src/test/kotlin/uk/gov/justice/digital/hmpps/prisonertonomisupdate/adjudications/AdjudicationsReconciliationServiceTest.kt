@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@SpringAPIServiceTest
@Import(
  AdjudicationsReconciliationService::class,
  NomisApiService::class,
  AdjudicationsApiService::class,
  AdjudicationsConfiguration::class,
)
internal class AdjudicationsReconciliationServiceTest {
  @MockBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var service: AdjudicationsReconciliationService

  @Nested
  inner class CheckBookingAdaPunishmentsMatch {

    @Nested
    inner class WhenBothSystemsHaveNoAdas {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(123456, emptyList())
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = emptyList(),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }
  }

  @Nested
  inner class GenerateReconciliationReport {
    @BeforeEach
    fun setUp() {
      nomisApi.stubGetActivePrisonersInitialCount(1)
      nomisApi.stubGetActivePrisonersPage(1, 0, 1)
      adjudicationsApiServer.stubGetAdjudicationsByBookingId(1, emptyList())
      nomisApi.stubGetAdaAwardSummary(
        bookingId = 1,
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(bookingId = 1, offenderNo = "A1234AA", prisonIds = listOf("MDI"), adaSummaries = emptyList()),
      )
    }

    @Test
    fun `will call DPS for each bookingId`() = runTest {
      service.generateReconciliationReport(1)
      adjudicationsApiServer.verify(getRequestedFor(urlPathEqualTo("/reported-adjudications/booking/1")))
    }

    @Test
    fun `will call NOMIS for each bookingId`() = runTest {
      service.generateReconciliationReport(1)
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/1/awards/ada/summary")))
    }
  }
}
