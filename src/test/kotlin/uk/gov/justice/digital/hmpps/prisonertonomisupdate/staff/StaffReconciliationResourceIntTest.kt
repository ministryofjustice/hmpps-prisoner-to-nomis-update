package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import java.util.UUID

class StaffReconciliationResourceIntTest(
  @Autowired
  private val nomisApi: StaffNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = StaffDpsApiExtension.dpsStaffServer

  @DisplayName("GET /staff/nomis-staff-id/{nomisStaffId}/reconciliation")
  @Nested
  inner class GetStaffReconciliationByNomisId {
    val nomisStaffId = 1234L
    val dpsStaffId = UUID.randomUUID().toString()

    @BeforeEach
    fun setUp() {
      stubStaff(nomisStaffId, dpsStaffId, nomisStaffDetails(), dpsStaffDetails())
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPathMatch {
      @Test
      fun `will not return mismatch`() {
        webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .isEmpty
      }
    }

    @Nested
    inner class HappyPathMismatch {

      @Nested
      inner class MismatchStaffMember {
        @BeforeEach
        fun setUp() {
          stubStaff(
            nomisStaffId,
            dpsStaffId,
            nomisStaffDetails().copy(firstName = "FRED"),
            dpsStaffDetails().copy(firstName = "BOB"),
          )
        }

        @Test
        fun `will return mismatch`() {
          webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nomisStaffId").isEqualTo(nomisStaffId)
            .jsonPath("$.dpsStaffId").isEqualTo(dpsStaffId)
            .jsonPath("differences").value<Map<String, String>> {
              assertThat(it).containsExactly(entry("firstName", "nomis=FRED, dps=BOB"))
            }
        }
      }

      @Nested
      inner class MismatchStaffUserAccount {
        @BeforeEach
        fun setUp() {
          stubStaff(
            nomisStaffId,
            dpsStaffId,
            nomisStaffDetails().copy(accounts = listOf(staffAccount().copy(typeCode = "GENERAL"))),
            dpsStaffDetails(),
          )
        }

        @Test
        fun `will return mismatch`() {
          webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nomisStaffId").isEqualTo(nomisStaffId)
            .jsonPath("$.dpsStaffId").isEqualTo(dpsStaffId)
            .jsonPath("differences").value<Map<String, String>> {
              assertThat(it).containsExactly(entry("accounts[0].typeCode", "nomis=GENERAL, dps=ADMIN"))
            }
        }
      }
    }

    @Nested
    inner class HappyPathNoDpsStaff {
      @Test
      fun `will return mismatch`() {
        dpsApi.stubGetStaff(response = null)
        webTestClient.get().uri("/staff/nomis-staff-id/$nomisStaffId/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.nomisStaffId").isEqualTo(nomisStaffId)
          .jsonPath("$.dpsStaffId").doesNotExist()
          .jsonPath("differences").value<Map<String, String>> {
            assertThat(it).containsExactly(
              entry("dps-record-missing", "true"),
            )
          }
      }
    }
  }

  fun stubStaff(nomisStaffId: Long, dpsStaffId: String, nomisStaff: StaffDetails, dpsStaff: PrisonUserReconciliationResponse) {
    nomisApi.stubGetStaffById(nomisStaffId, response = nomisStaff.copy(id = nomisStaffId))
    dpsApi.stubGetStaff(nomisStaffId, response = dpsStaff.copy(userId = UUID.fromString(dpsStaffId)))
  }
}
