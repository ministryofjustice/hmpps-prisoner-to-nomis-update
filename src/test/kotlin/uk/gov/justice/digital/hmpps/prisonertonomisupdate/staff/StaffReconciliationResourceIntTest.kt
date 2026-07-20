package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails

class StaffReconciliationResourceIntTest(
  @Autowired
  private val nomisApi: StaffNomisApiMockServer,

  @Autowired
  private val mappingService: StaffMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = StaffDpsApiExtension.dpsStaffServer

  @DisplayName("GET /staff/nomis-staff-id/{nomisStaffId}/reconciliation")
  @Nested
  inner class GetStaffReconciliationByNomisId {
    @BeforeEach
    fun setUp() {
      stubStaff(1234, "4321", staffDetails(), dpsStaffDetails())
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPathNoMismatch {
      @Test
      fun `will not return mismatch`() {
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .isEmpty
      }
    }

    @Nested
    inner class HappyPathMismatch {
      @BeforeEach
      fun setUp() {
        stubStaff(
          1234,
          "4321",
          staffDetails().copy(firstName = "FRED"),
          dpsStaffDetails().copy(user = dpsStaffUser().copy(firstName = "BOB")),
        )
      }

      @Test
      fun `will return mismatch`() {
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.nomisStaffId").isEqualTo(1234)
          .jsonPath("$.dpsStaffId").isEqualTo("4321")
          .jsonPath("$.reason").isEqualTo("different-staff-details")
          .jsonPath("$.nomisStaff.firstName").isEqualTo("FRED")
          .jsonPath("$.dpsStaff.firstName").isEqualTo("BOB")
      }

      // TODO Additional tests for reconciliation differences
    }

    @Nested
    inner class HappyPathNoDpsMapping {
      @Test
      fun `will return mismatch`() {
        mappingService.stubGetStaffByNomisIdOrNull(mapping = null)
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.nomisStaffId").isEqualTo(1234)
          .jsonPath("$.reason").isEqualTo("staff-mapping-missing")
      }
    }

    @Nested
    inner class HappyPathNoDpsStaff {
      @Test
      fun `will return mismatch`() {
        dpsApi.stubGetStaff(response = null)
        webTestClient.get().uri("/staff/nomis-staff-id/1234/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.nomisStaffId").isEqualTo(1234)
          .jsonPath("$.dpsStaffId").isEqualTo("4321")
          .jsonPath("$.reason").isEqualTo("dps-record-missing")
      }
    }
  }

  fun stubStaff(nomisStaffId: Long, dpsStaffId: String, nomisStaff: StaffDetails, dpsStaff: DpsStaffDetails) {
    nomisApi.stubGetStaffById(nomisStaffId, response = nomisStaff.copy(id = nomisStaffId))
    dpsApi.stubGetStaff(dpsStaffId, response = dpsStaff.copy(user = dpsStaff.user.copy(id = dpsStaffId)))
    mappingService.stubGetStaffByNomisIdOrNull(
      nomisStaffId = nomisStaffId,
      mapping = StaffMappingDto(
        dpsId = dpsStaffId,
        nomisId = nomisStaffId,
        mappingType = StaffMappingDto.MappingType.MIGRATED,
      ),
    )
  }
}
