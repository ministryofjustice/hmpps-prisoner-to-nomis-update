package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase

class CalculateReleaseDatesReconciliationIntTest : IntegrationTestBase() {

  @Nested
  inner class ReconciliationEndpoint {

    @Nested
    inner class HappyPath {
      @Test
      fun `should return no mismatches`() {
        webTestClient.get().uri("/calculate-release-dates/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json("[]")
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/calculate-release-dates/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/calculate-release-dates/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/calculate-release-dates/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }
  }
}
