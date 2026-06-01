package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.courtSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.*

@SpringAPIServiceTest
@Import(
  CourtSchedulerDpsApiService::class,
  CourtSchedulerDpsApiMockServer::class,
  CourtSchedulerConfiguration::class,
  RetryApiService::class,
)
class CourtSchedulerDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSchedulerDpsApiService

  @Nested
  inner class GetCourtSchedulerReconciliationDetail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val personIdentifier = "A1234BC"
      courtSchedulerDpsApiServer.stubGetCourtSchedulerReconciliation(personIdentifier)

      apiService.getCourtSchedulerReconciliation(personIdentifier)

      courtSchedulerDpsApiServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val personIdentifier = "A1234BC"
      courtSchedulerDpsApiServer.stubGetCourtSchedulerReconciliation(personIdentifier)

      apiService.getCourtSchedulerReconciliation(personIdentifier)

      courtSchedulerDpsApiServer.verify(
        getRequestedFor(urlPathEqualTo("/reconciliation/court-appearances/$personIdentifier")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val personIdentifier = "A1234BC"
      courtSchedulerDpsApiServer.stubGetCourtSchedulerReconciliation(personIdentifier)

      with(apiService.getCourtSchedulerReconciliation(personIdentifier)) {
        assertThat(courtEvents.size).isEqualTo(1)
        assertThat(courtEvents[0].courtEvent.agyLocId).isEqualTo("LEEDMC")
        assertThat(courtEvents[0].movements[0].fromAgencyId).isEqualTo("BXI")
        assertThat(courtEvents[0].movements[1].toAgencyId).isEqualTo("BXI")
        assertThat(unscheduledMovements.size).isEqualTo(2)
        assertThat(unscheduledMovements[0].directionCode).isEqualTo("OUT")
        assertThat(unscheduledMovements[1].directionCode).isEqualTo("IN")
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val personIdentifier = "A1234BC"
      courtSchedulerDpsApiServer.stubGetCourtSchedulerReconciliation(personIdentifier, status = 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtSchedulerReconciliation(personIdentifier)
      }
    }
  }

  @Nested
  inner class GetCourtAppearance {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val id = UUID.randomUUID()
      courtSchedulerDpsApiServer.stubGetCourtAppearance(id)

      apiService.getCourtAppearance(id)

      courtSchedulerDpsApiServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val id = UUID.randomUUID()
      courtSchedulerDpsApiServer.stubGetCourtAppearance(id)

      apiService.getCourtAppearance(id)

      courtSchedulerDpsApiServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/court-appearances/$id")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val id = UUID.randomUUID()
      courtSchedulerDpsApiServer.stubGetCourtAppearance(id)

      with(apiService.getCourtAppearance(id)) {
        assertThat(this.dpsId).isEqualTo(id)
        assertThat(agyLocId).isEqualTo("LEEDMC")
        assertThat(eventStatus).isEqualTo("COMPLETED")
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val id = UUID.randomUUID()
      courtSchedulerDpsApiServer.stubGetCourtAppearance(status = 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtAppearance(id)
      }
    }
  }
}
