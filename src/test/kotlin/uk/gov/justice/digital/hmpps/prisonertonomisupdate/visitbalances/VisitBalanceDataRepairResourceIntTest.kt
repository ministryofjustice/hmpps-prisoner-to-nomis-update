package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension.Companion.visitBalanceDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

class VisitBalanceDataRepairResourceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var visitBalanceNomisApi: VisitBalanceNomisApiMockServer

  @DisplayName("POST /prisoners/{offenderNo}/visit-balance/repair")
  @Nested
  inner class RepairVisitBalance {
    val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        nomisApi.stubCheckServicePrisonForPrisoner(prisonNumber = prisonNumber)
        visitBalanceDpsApi.stubGetVisitBalance()
        visitBalanceNomisApi.stubPutVisitBalance(prisonNumber = prisonNumber)

        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf("NOMIS_VISIT_BALANCE")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will perform VISIT_ALLOCATION service agency check`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/service-prisons/VISIT_ALLOCATION/prisoner/$prisonNumber")))
      }

      @Test
      fun `will retrieve the balance from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$prisonNumber/balance")))
      }

      @Test
      fun `will update the balance in Nomis`() {
        visitBalanceNomisApi.verify(
          putRequestedFor(urlPathEqualTo("/prisoners/$prisonNumber/visit-balance"))
            .withRequestBody(matchingJsonPath("remainingVisitOrders", equalTo("24")))
            .withRequestBody(matchingJsonPath("remainingPrivilegedVisitOrders", equalTo("3"))),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-synchronisation-repair"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["voBalance"]).isEqualTo("24")
            assertThat(it["pvoBalance"]).isEqualTo("3")
          },
          isNull(),
        )
      }
    }
  }
}
