package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse

class BedAssignmentReconciliationResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: BedAssignmentNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: BedAssignmentMappingApiMockServer

  private val endpoint = "/bed-assignments/reconciliation/A0001AA"

  @Nested
  inner class Security {
    @Test
    fun `unauthorised without token`() {
      webTestClient.get().uri(endpoint).exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `forbidden without role`() {
      webTestClient.get().uri(endpoint)
        .headers(setAuthorisation())
        .exchange().expectStatus().isForbidden
    }

    @Test
    fun `forbidden with wrong role`() {
      webTestClient.get().uri(endpoint)
        .headers(setAuthorisation(roles = listOf("BANANAS")))
        .exchange().expectStatus().isForbidden
    }
  }

  @Nested
  inner class ReconcilePrisoner {
    @BeforeEach
    fun setUp() {
      nomisApi.stubGetBedAssignmentsForPrisoner(
        "A0001AA",
        PrisonerBedAssignmentHistoryResponse(listOf(nomisBedAssignment().copy(livingUnitDescription = "MDI-A-1-001"))),
      )
      mappingApi.stubGetByNomisId(12345, 1)
    }

    @Test
    fun `returns an empty body when assignments match`() {
      bedAssignmentApi.stubGetBedAssignments("A0001AA", listOf(dpsBedAssignment()))

      webTestClient.get().uri(endpoint)
        .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
        .exchange().expectStatus().isOk
        .expectBody().isEmpty
    }

    @Test
    fun `returns differences for one prisoner`() {
      bedAssignmentApi.stubGetBedAssignments("A0001AA", listOf(dpsBedAssignment().copy(locationKey = "MDI-B-1-001")))

      webTestClient.get().uri(endpoint)
        .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
        .exchange().expectStatus().isOk
        .expectBody()
        .jsonPath("nomis[0].id").isEqualTo("12345-1")
        .jsonPath("dps[0].id").isEqualTo("de91dfa7-821f-4552-a427-bf2f32eafeb0")
        .jsonPath("differences[0].property").isEqualTo("bed-assignments[12345-1].living-unit")
        .jsonPath("differences[0].nomis").isEqualTo("MDI-A-1-001")
        .jsonPath("differences[0].dps").isEqualTo("MDI-B-1-001")
    }
  }
}
