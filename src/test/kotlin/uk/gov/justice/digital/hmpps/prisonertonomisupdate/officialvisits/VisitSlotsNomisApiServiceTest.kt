package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.VisitSlotsNomisApiMockServer.Companion.createVisitTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(VisitSlotsNomisApiService::class, OfficialVisitsConfiguration::class, VisitSlotsNomisApiMockServer::class, RetryApiService::class)
class VisitSlotsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitSlotsNomisApiService

  @Autowired
  private lateinit var mockServer: VisitSlotsNomisApiMockServer

  @Nested
  inner class GetTimeSlotsForPrison {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetTimeSlotsForPrison(prisonId = "BXI")

      apiService.getTimeSlotsForPrison(prisonId = "BXI")

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get slots endpoint`() = runTest {
      mockServer.stubGetTimeSlotsForPrison(prisonId = "BXI")

      apiService.getTimeSlotsForPrison(prisonId = "BXI")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/BXI")),
      )
    }

    @Test
    fun `will call the get slots endpoint with parameters`() = runTest {
      mockServer.stubGetTimeSlotsForPrison(prisonId = "BXI")

      apiService.getTimeSlotsForPrison(prisonId = "BXI", activeOnly = true)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/BXI"))
          .withQueryParam("activeOnly", equalTo("true")),
      )
    }
  }

  @Nested
  inner class GetActivePrisonsWithTimeSlots {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetActivePrisonsWithTimeSlots()

      apiService.getActivePrisonsWithTimeSlots()

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get prisons endpoint`() = runTest {
      mockServer.stubGetActivePrisonsWithTimeSlots()

      apiService.getActivePrisonsWithTimeSlots()

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visits/configuration/prisons")),
      )
    }
  }

  @Nested
  inner class CreateTimeSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubCreateTimeSlot(
        prisonId = "MDI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot.MON,
      )

      apiService.createTimeSlot(
        prisonId = "MDI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot.MON,
        request = createVisitTimeSlotRequest(),
      )

      mockServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get prisons endpoint`() = runTest {
      mockServer.stubCreateTimeSlot(
        prisonId = "MDI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot.MON,
      )

      apiService.createTimeSlot(
        prisonId = "MDI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot.MON,
        request = createVisitTimeSlotRequest(),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/MDI/day-of-week/MON")),
      )
    }
  }
}
