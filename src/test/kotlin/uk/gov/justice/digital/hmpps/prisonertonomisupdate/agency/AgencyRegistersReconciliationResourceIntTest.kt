package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.expectBodyResponse

class AgencyRegistersReconciliationResourceIntTest(
  @Autowired private val nomisApi: AgencyNomisApiMockServer,
) : IntegrationTestBase() {
  private val dpsApi = AgencyRegistersDpsApiExtension.agencyRegistersApi

  @DisplayName("GET /agencies/{agencyId}/reconciliation")
  @Nested
  inner class GetAgencyReconciliation {
    private val agencyId = "SHEFCC"
    private val url = "/agencies/$agencyId/reconciliation"

    @BeforeEach
    fun setUp() {
      nomisApi.stubGetAgency(agencyId, agencyResponse())
      dpsApi.stubGetAgencyForReconciliation(agencyId, legacyAgencyDto())
    }

    @Nested
    inner class Security {
      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri(url)
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri(url)
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class WhenAgenciesMatch {
      @Test
      fun `will return an empty response`() {
        getReconciliation()
          .expectStatus().isOk
          .expectBody().isEmpty
      }
    }

    @Nested
    inner class WhenAgenciesDoNotMatch {
      private val dpsAgency = legacyAgencyDto().copy(name = "Sheffield Big Court")

      @BeforeEach
      fun setUp() {
        dpsApi.stubGetAgencyForReconciliation(agencyId, dpsAgency)
      }

      @Test
      fun `will return the full agency payloads and mismatch details`() {
        val result = getReconciliation().expectBodyResponse<MismatchAgency>()

        assertThat(result.agencyId).isEqualTo(agencyId)
        assertThat(result.reason).isEqualTo("different-core-agency-details")
        assertThat(result.nomis).contains("Sheffield Crown Ct")
        assertThat(result.dps).contains("Sheffield Big Court")
        assertThat(result.nomisAgency).isEqualTo(agencyResponse())
        assertThat(result.dpsAgency).isEqualTo(dpsAgency)
      }
    }

    @Nested
    inner class WhenMissingInNomis {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAgency(agencyId, HttpStatus.NOT_FOUND)
      }

      @Test
      fun `will return the DPS agency payload`() {
        val result = getReconciliation().expectBodyResponse<MismatchAgency>()

        assertThat(result.agencyId).isEqualTo(agencyId)
        assertThat(result.reason).isEqualTo("Missing in NOMIS")
        assertThat(result.nomisAgency).isNull()
        assertThat(result.dpsAgency).isEqualTo(legacyAgencyDto())
      }
    }

    @Nested
    inner class WhenMissingInDps {
      @BeforeEach
      fun setUp() {
        dpsApi.stubGetAgencyForReconciliation(agencyId, HttpStatus.NOT_FOUND)
      }

      @Test
      fun `will return the NOMIS agency payload`() {
        val result = getReconciliation().expectBodyResponse<MismatchAgency>()

        assertThat(result.agencyId).isEqualTo(agencyId)
        assertThat(result.reason).isEqualTo("Missing in DPS")
        assertThat(result.nomisAgency).isEqualTo(agencyResponse())
        assertThat(result.dpsAgency).isNull()
      }
    }

    @Nested
    inner class WhenNomisFails {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAgency(agencyId, HttpStatus.INTERNAL_SERVER_ERROR)
      }
    }

    @Nested
    inner class WhenDpsFails {
      @BeforeEach
      fun setUp() {
        dpsApi.stubGetAgencyForReconciliation(agencyId, HttpStatus.INTERNAL_SERVER_ERROR)
      }
    }

    private fun getReconciliation() = webTestClient.get().uri(url)
      .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
      .exchange()
  }
}
