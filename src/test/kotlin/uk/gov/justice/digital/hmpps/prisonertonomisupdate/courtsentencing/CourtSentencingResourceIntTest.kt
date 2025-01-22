package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

private const val NOMIS_COURT_CASE_ID = 7L
private const val DPS_COURT_CASE_ID = "4321"
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_NEXT_COURT_APPEARANCE_ID = 9L
private const val NOMIS_COURT_CHARGE_ID = 11L
private const val NOMIS_COURT_CHARGE_2_ID = 12L
private const val NOMIS_COURT_CHARGE_3_ID = 13L
private const val NOMIS_COURT_CHARGE_4_ID = 14L
private const val NOMIS_COURT_CHARGE_5_ID = 15L
private const val NOMIS_COURT_CHARGE_6_ID = 16L
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_2_ID = "9996aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_3_ID = "4566aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_4_ID = "1236aa44-642a-484a-a967-2d17b5c9c5a1"
private const val OFFENDER_NO = "AB12345"
private const val DONCASTER_COURT_CODE = "DRBYYC"
private const val PRISON_ID = "MDI"
private const val CASE_REFERENCE = "ABC4999"
class CourtSentencingResourceIntTest : SqsIntegrationTestBase() {
  @DisplayName("GET /court-sentencing/court-cases/dps-case-id/{dpsCaseId}/reconciliation")
  @Nested
  inner class ManualCaseReconciliationByDpsCaseId {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          DPS_COURT_CASE_ID,
          legacyCourtCaseResponse(
            startDate = LocalDate.of(2024, 1, 1),
            active = true,
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch`() {
          nomisApi.stubGetCourtCase(caseId = NOMIS_COURT_CASE_ID, caseStatus = CodeDescription("A", "Active"), beginDate = LocalDate.parse("2024-01-02"))

          webTestClient.get().uri("/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("mismatch.nomisCase.startDate").isEqualTo("2024-01-02")
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("mismatch.nomisCase.active").isEqualTo("true")
            .jsonPath("mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("mismatch.dpsCase.startDate").isEqualTo("2024-01-01")
            .jsonPath("mismatch.dpsCase.active").isEqualTo("true")
            .jsonPath("mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
        }
      }

      @Nested
      inner class MismatchNotFound {
        @Test
        fun `will return a response to indicate no mismatch`() {
          nomisApi.stubGetCourtCase(caseId = NOMIS_COURT_CASE_ID, caseStatus = CodeDescription("A", "Active"), beginDate = LocalDate.parse("2024-01-01"))

          webTestClient.get().uri("/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("mismatch").doesNotExist()
        }
      }
    }
  }

  @DisplayName("GET /court-sentencing/court-cases/nomis-case-id/{nomisCaseId}/reconciliation")
  @Nested
  inner class ManualCaseReconciliationByNomisCaseId {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenNomisId(
          id = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
        )
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          DPS_COURT_CASE_ID,
          legacyCourtCaseResponse(
            startDate = LocalDate.of(2024, 1, 1),
            active = true,
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch`() {
          nomisApi.stubGetCourtCase(caseId = NOMIS_COURT_CASE_ID, caseStatus = CodeDescription("I", "Inactive"), beginDate = LocalDate.parse("2024-01-01"))

          webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("mismatch.nomisCase.startDate").isEqualTo("2024-01-01")
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("mismatch.nomisCase.active").isEqualTo("false")
            .jsonPath("mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("mismatch.dpsCase.startDate").isEqualTo("2024-01-01")
            .jsonPath("mismatch.dpsCase.active").isEqualTo("true")
            .jsonPath("mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
        }
      }

      @Nested
      inner class MismatchNotFound {
        @Test
        fun `will return a response to indicate no mismatch`() {
          nomisApi.stubGetCourtCase(caseId = NOMIS_COURT_CASE_ID, caseStatus = CodeDescription("A", "Active"), beginDate = LocalDate.parse("2024-01-01"))

          webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("mismatch").doesNotExist()
        }
      }
    }
  }
  fun legacyCourtCaseResponse(startDate: LocalDate, active: Boolean) = LegacyCourtCase(
    courtCaseUuid = DPS_COURT_CASE_ID,
    prisonerId = OFFENDER_NO,
    courtId = DONCASTER_COURT_CODE,
    caseReference = CASE_REFERENCE,
    startDate = startDate,
    active = active,
    caseReferences = listOf(
      CaseReferenceLegacyData(
        offenderCaseReference = CASE_REFERENCE,
        updatedDate = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
      ),
    ),
  )
}
