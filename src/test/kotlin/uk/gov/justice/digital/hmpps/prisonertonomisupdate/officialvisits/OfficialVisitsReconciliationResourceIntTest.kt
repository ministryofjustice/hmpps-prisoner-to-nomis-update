package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import java.time.LocalDate
import java.time.LocalDateTime

class OfficialVisitsReconciliationResourceIntTest(
  @Autowired
  private val nomisApi: OfficialVisitsNomisApiMockServer,

  @Autowired
  private val mappingService: OfficialVisitsMappingApiMockServer,

) : IntegrationTestBase() {

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("GET /official-visits/nomis-visit-id/{nomisVisitId}/reconciliation")
  @Nested
  inner class GetOfficialVisitsReconciliationByNomisId {
    @BeforeEach
    fun setUp() {
      stubVisits(1, 1234, officialVisitResponse().copy(prisonId = "WWI"), syncOfficialVisit().copy(prisonCode = "BXI"))
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/official-visits/nomis-visit-id/1/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/official-visits/nomis-visit-id/1/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/official-visits/nomis-visit-id/1/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `will return mismatch`() {
        webTestClient.get().uri("/official-visits/nomis-visit-id/1/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.nomisVisitId").isEqualTo(1)
          .jsonPath("$.reason").isEqualTo("different-visit-details")
          .jsonPath("$.nomisVisit.prisonId").isEqualTo("WWI")
          .jsonPath("$.dpsVisit.prisonId").isEqualTo("BXI")
      }
    }
  }

  @DisplayName("GET /prisoners/{offenderNo}/official-visits/reconciliation")
  @Nested
  inner class GetOfficialVisitsReconciliationByPrisonerNumber {

    @BeforeEach
    fun setUp() {
      nomisApi.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A0003TZ",
        response = listOf(
          officialVisitResponse().copy(
            visitId = 4,
            offenderNo = "A0003TZ",
            startDateTime = LocalDateTime.parse("2020-01-01T17:01"),
          ),
          officialVisitResponse().copy(visitId = 5, offenderNo = "A0003TZ"),
        ),
      )
      dpsApi.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A0003TZ",
        response = listOf(
          syncOfficialVisit().copy(
            officialVisitId = 4,
            prisonerNumber = "A0003TZ",
            startTime = "17:00",
            visitDate = LocalDate.parse("2020-01-01"),
          ),
          syncOfficialVisit().copy(officialVisitId = 5, prisonerNumber = "A0003TZ"),
        ),
      )
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoners/A0003TZ/official-visits/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoners/A0003TZ/official-visits/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoners/A0003TZ/official-visits/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `will return mismatches`() {
        webTestClient.get().uri("/prisoners/A0003TZ/official-visits/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].reason").isEqualTo("different-visit-details")
          .jsonPath("$[0].nomisVisitId").isEqualTo("4")
      }
    }
  }

  fun stubVisits(nomisVisitId: Long, dpsVisitId: Long, nomisVisit: OfficialVisitResponse, dpsVisit: SyncOfficialVisit) {
    nomisApi.stubGetOfficialVisit(nomisVisitId, response = nomisVisit.copy(visitId = nomisVisitId))
    dpsApi.stubGetOfficialVisit(dpsVisitId, response = dpsVisit.copy(officialVisitId = dpsVisitId))
    mappingService.stubGetVisitByNomisIdOrNull(
      nomisVisitId = nomisVisitId,
      mapping = OfficialVisitMappingDto(
        dpsId = dpsVisitId.toString(),
        nomisId = nomisVisitId,
        mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
      ),
    )
  }
}
