package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.TestCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 7L
private const val NOMIS_COURT_CASE_2_ID = 8L
private const val DPS_COURT_CASE_ID = "4321"
private const val DPS_COURT_CASE_2_ID = "4321"
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_COURT_CHARGE_ID = 11L
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val OFFENDER_NO = "AB12345"
private const val PRISON_MDI = "MDI"
private const val PRISON_LEI = "LEI"
private const val CASE_REFERENCE = "ABC4999"
private const val CASE_REFERENCE2 = "ABC4888"
private const val OFFENCE_CODE_1 = "TR11017"
private const val OFFENCE_CODE_2 = "PR52028A"
private const val OUTCOME_1 = "4001"
private const val OUTCOME_2 = "3001"

class CourtSentencingResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApi: CourtSentencingNomisApiMockServer

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
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation2(
          DPS_COURT_CASE_ID,
          dpsCourtCaseResponse(
            active = true,
            appearances = listOf(
              dpsAppearanceResponse(
                charges = listOf(
                  dpsChargeResponse(),
                ),
              ),
            ),
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch for case`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            caseStatus = CodeDescription("I", "Inactive"),
            beginDate = LocalDate.parse("2024-01-02"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

          webTestClient.get().uri("/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("mismatch.nomisCase.active").isEqualTo("false")
            .jsonPath("mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("mismatch.dpsCase.active").isEqualTo("true")
            .jsonPath("mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
        }
      }

      @Nested
      inner class MismatchNotFound {
        @Test
        fun `will return a response to indicate no mismatch`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            beginDate = LocalDate.parse("2024-01-02"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

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
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation2(
          DPS_COURT_CASE_ID,
          dpsCourtCaseResponse(
            active = true,
            appearances = listOf(
              dpsAppearanceResponse(
                appearanceDate = LocalDate.parse("2024-01-01"),
                charges = listOf(
                  dpsChargeResponse(offenceCode = OFFENCE_CODE_2),
                  dpsChargeResponse(),
                ),
              ),
              dpsAppearanceResponse(
                appearanceDate = LocalDate.parse("2024-02-01"),
                charges = listOf(
                  dpsChargeResponse(),
                ),
              ),
            ),
            caseReferences = listOf(
              CaseReferenceLegacyData(
                offenderCaseReference = CASE_REFERENCE2,
                updatedDate = LocalDateTime.parse("2024-01-01T10:10:00"),
              ),
              CaseReferenceLegacyData(
                offenderCaseReference = CASE_REFERENCE,
                updatedDate = LocalDateTime.parse("2024-01-01T10:10:00"),
              ),
            ),
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch when appearance outcome is different`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                outcome = OffenceResultCodeResponse(
                  code = OUTCOME_2,
                  description = "Outcome text",
                  dispositionCode = "F",
                  chargeStatus = "A",
                  conviction = true,
                ),
                charges = listOf(
                  nomisChargeResponse(),
                  nomisChargeResponse(offenceCode = OFFENCE_CODE_2),
                ),
              ),
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
            caseIndentifiers = listOf(
              CaseIdentifierResponse(
                reference = CASE_REFERENCE,
                createDateTime = LocalDateTime.parse("2024-01-01T10:10:00"),
                type = "CASE/INFO#",
              ),
              CaseIdentifierResponse(
                reference = CASE_REFERENCE2,
                createDateTime = LocalDateTime.parse("2024-01-01T10:10:00"),
                type = "CASE/INFO#",
              ),
            ),
          )

          webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("mismatch.differences.size()").isEqualTo(1)
            // outcome on first appearance is different
            .jsonPath("mismatch.differences[0].dps").isEqualTo(4001)
            .jsonPath("mismatch.differences[0].nomis").isEqualTo(3001)
            .jsonPath("mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
        }

        @Test
        fun `will return a mismatch when a charge (court event charge) date is different`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                  nomisChargeResponse(offenceCode = OFFENCE_CODE_2, offenceStartDate = LocalDate.of(2023, 10, 10)),
                ),
              ),
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

          webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("mismatch.differences.size()").isEqualTo(2)
            // start and end dates on second charge are different
            .jsonPath("mismatch.differences[0].id").isEqualTo(DPS_COURT_CHARGE_ID)
            .jsonPath("mismatch.differences[0].dps").isEqualTo("2023-01-01")
            .jsonPath("mismatch.differences[0].property").isEqualTo("case.appearances[0].charges[0].offenceDate")
            .jsonPath("mismatch.differences[0].nomis").isEqualTo("2023-10-10")
            .jsonPath("mismatch.differences[1].id").isEqualTo(DPS_COURT_CHARGE_ID)
            .jsonPath("mismatch.differences[1].dps").isEqualTo("2023-01-02")
            .jsonPath("mismatch.differences[1].nomis").isEqualTo("2023-10-11")
            .jsonPath("mismatch.differences[1].property").isEqualTo("case.appearances[0].charges[0].offenceEndDate")
            .jsonPath("mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
        }

        @Test
        fun `will return a mismatch when number of appearances differ`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

          webTestClient.get().uri("/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("mismatch.differences.size()").isEqualTo(1)
            // start and end dates on second charge are different
            .jsonPath("mismatch.differences[0].property").isEqualTo("case.appearances")
            .jsonPath("mismatch.differences[0].dps").isEqualTo(2)
            .jsonPath("mismatch.differences[0].nomis").isEqualTo(1)
        }
      }

      @Nested
      inner class MismatchNotFound {
        @Test
        fun `will return a response to indicate no mismatch`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            caseStatus = CodeDescription("A", "Active"),
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                  nomisChargeResponse(offenceCode = OFFENCE_CODE_2),

                ),
              ),
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

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

  @DisplayName("GET /prisoners/{offenderNo}/court-sentencing/court-cases/reconciliation")
  @Nested
  inner class ManualCaseReconciliationByOffenderId {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/reconciliation")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/reconciliation")
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

        mappingServer.stubGetCourtCaseMappingGivenNomisId(
          id = NOMIS_COURT_CASE_2_ID,
          dpsCourtCaseId = DPS_COURT_CASE_2_ID,
        )

        courtSentencingNomisApi.stubGetCourtCasesByOffenderNo(
          offenderNo = OFFENDER_NO,
          response = listOf(
            nomisCaseResponse(
              id = NOMIS_COURT_CASE_ID,
              beginDate = LocalDate.parse("2024-01-01"),
            ),
            nomisCaseResponse(
              id = NOMIS_COURT_CASE_2_ID,
              beginDate = LocalDate.parse("2024-01-01"),
            ),
          ),
        )

        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation2(
          DPS_COURT_CASE_ID,
          dpsCourtCaseResponse(
            active = true,
            appearances = listOf(
              dpsAppearanceResponse(
                appearanceDate = LocalDate.parse("2024-01-01"),
                charges = listOf(
                  dpsChargeResponse(offenceCode = OFFENCE_CODE_2),
                  dpsChargeResponse(),
                ),
              ),
              dpsAppearanceResponse(
                appearanceDate = LocalDate.parse("2024-02-01"),
                charges = listOf(
                  dpsChargeResponse(),
                ),
              ),
            ),
          ),
        )
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation2(
          DPS_COURT_CASE_ID,
          dpsCourtCaseResponse(
            active = true,
            appearances = listOf(
              dpsAppearanceResponse(
                appearanceDate = LocalDate.parse("2024-01-01"),
                charges = listOf(
                  dpsChargeResponse(offenceCode = OFFENCE_CODE_2),
                  dpsChargeResponse(),
                ),
              ),
              dpsAppearanceResponse(
                appearanceDate = LocalDate.parse("2024-02-01"),
                charges = listOf(
                  dpsChargeResponse(),
                ),
              ),
            ),
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch when appearance outcome is different`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                outcome = OffenceResultCodeResponse(
                  code = OUTCOME_2,
                  description = "Outcome text",
                  dispositionCode = "F",
                  chargeStatus = "A",
                  conviction = true,
                ),
                charges = listOf(
                  nomisChargeResponse(),
                  nomisChargeResponse(offenceCode = OFFENCE_CODE_2),
                ),
              ),
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

          // second case is not a mismatch
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_2_ID,
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                  nomisChargeResponse(offenceCode = OFFENCE_CODE_2),
                ),
              ),
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

          webTestClient.get().uri("prisoners/$OFFENDER_NO/court-sentencing/court-cases/reconciliation")
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("$[0].mismatch.differences.size()").isEqualTo(1)
            // outcome on first appearance is different
            .jsonPath("$[0]mismatch.differences[0].dps").isEqualTo(4001)
            .jsonPath("$[0].mismatch.differences[0].nomis").isEqualTo(3001)
            .jsonPath("$[0].mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("$[0].mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
        }
      }

      @Nested
      inner class MismatchNotFound {
        @Test
        fun `will return a response to indicate no mismatch`() {
          courtSentencingNomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            caseStatus = CodeDescription("A", "Active"),
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                  nomisChargeResponse(offenceCode = OFFENCE_CODE_2),

                ),
              ),
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
                charges = listOf(
                  nomisChargeResponse(),
                ),
              ),
            ),
          )

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

  @DisplayName("GET /prisoners/{offenderNo}/court-sentencing/court-charges/repair")
  @Nested
  inner class ChargeInsertedRepair {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/court-sentencing/court-charges/repair")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(
            BodyInserters.fromValue(
              CourtChargeRequest(
                offenderNo = OFFENDER_NO,
                dpsChargeId = DPS_COURT_CHARGE_ID,
                dpsCaseId = DPS_COURT_CASE_ID,
              ),
            ),
          )
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/court-sentencing/court-charges/repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .body(
            BodyInserters.fromValue(
              CourtChargeRequest(
                offenderNo = OFFENDER_NO,
                dpsChargeId = DPS_COURT_CHARGE_ID,
                dpsCaseId = DPS_COURT_CASE_ID,
              ),
            ),
          )
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/court-sentencing/court-charges/repair")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
            BodyInserters.fromValue(
              CourtChargeRequest(
                offenderNo = OFFENDER_NO,
                dpsChargeId = DPS_COURT_CHARGE_ID,
                dpsCaseId = DPS_COURT_CASE_ID,
              ),
            ),
          )
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCharge(
          DPS_COURT_CHARGE_ID,
          offenderNo = OFFENDER_NO,
          caseID = DPS_COURT_CASE_ID,
        )
        nomisApi.stubCourtChargeCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID,
          """{ "offenderChargeId": $NOMIS_COURT_CHARGE_ID }""",
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)
        mappingServer.stubCreateCourtCharge()

        webTestClient.post().uri("/court-sentencing/court-charges/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_SENTENCING")))
          .contentType(MediaType.APPLICATION_JSON)
          .body(
            BodyInserters.fromValue(
              CourtChargeRequest(
                offenderNo = OFFENDER_NO,
                dpsChargeId = DPS_COURT_CHARGE_ID,
                dpsCaseId = DPS_COURT_CASE_ID,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("charge-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(CourtChargeMappingDto.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            Assertions.assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Charge`() {
        waitForAnyProcessingToComplete()
        nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID}/charges")))
      }

      @Test
      fun `will create a mapping between the two charges`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtChargeId",
                  WireMock.equalTo(DPS_COURT_CHARGE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }
  }

  fun dpsCourtCaseResponse(
    active: Boolean,
    appearances: List<LegacyCourtAppearance> = emptyList(),
    caseReferences: List<CaseReferenceLegacyData> = emptyList(),
  ) = TestCourtCase(
    courtCaseUuid = DPS_COURT_CASE_ID,
    prisonerId = OFFENDER_NO,
    active = true,
    appearances = appearances,
    caseReferences = caseReferences,
  )

  fun dpsAppearanceResponse(
    outcome: String = OUTCOME_1,
    charges: List<LegacyCharge> = emptyList(),
    appearanceDate: LocalDate = LocalDate.of(2024, 1, 1),
  ) = LegacyCourtAppearance(
    lifetimeUuid = UUID.fromString(DPS_COURT_APPEARANCE_ID),
    courtCaseUuid = DPS_COURT_CASE_ID,
    courtCode = PRISON_LEI,
    appearanceDate = appearanceDate,
    appearanceTime = "10:10",
    nomisOutcomeCode = outcome,
    charges = charges,
    prisonerId = OFFENDER_NO,
    nextCourtAppearance = LegacyNextCourtAppearance(
      appearanceDate = LocalDate.of(2024, 2, 1),
      appearanceTime = "10:10",
      courtId = PRISON_MDI,
    ),
  )

  fun nomisCaseResponse(
    id: Long = NOMIS_COURT_CASE_ID,
    beginDate: LocalDate = LocalDate.of(2024, 1, 1),
    events: List<CourtEventResponse> = emptyList(),
  ) = CourtCaseResponse(
    id = id,
    offenderNo = OFFENDER_NO,
    courtEvents = events,
    courtId = PRISON_LEI,
    createdDateTime = LocalDateTime.now(),
    createdByUsername = "Q1251T",
    caseStatus = CodeDescription("A", "Active"),
    legalCaseType = CodeDescription("CRT", "Court Appearance"),
    beginDate = beginDate,
    bookingId = 1,
    lidsCaseNumber = 1,
    offenderCharges = emptyList(),
    caseSequence = 1,
    caseInfoNumbers = emptyList(),
    sentences = emptyList(),
  )

  fun nomisAppearanceResponse(
    id: Long = NOMIS_COURT_APPEARANCE_ID,
    outcome: OffenceResultCodeResponse = OffenceResultCodeResponse(
      code = OUTCOME_1,
      description = "Outcome text",
      dispositionCode = "F",
      chargeStatus = "A",
      conviction = true,
    ),
    eventDateTime: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
    nextEventDateTime: LocalDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
    charges: List<CourtEventChargeResponse> = emptyList(),
  ) = CourtEventResponse(
    id = id,
    offenderNo = OFFENDER_NO,
    caseId = NOMIS_COURT_CASE_ID,
    courtId = PRISON_LEI,
    courtEventCharges = charges,
    createdDateTime = LocalDateTime.now(),
    createdByUsername = "Q1251T",
    courtEventType = CodeDescription("CRT", "Court Appearance"),
    outcomeReasonCode = outcome,
    eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
    eventDateTime = eventDateTime,
    courtOrders = emptyList(),
    nextEventDateTime = nextEventDateTime,
  )

  fun nomisChargeResponse(
    eventId: Long = NOMIS_COURT_APPEARANCE_ID,
    offenderChargeId: Long = NOMIS_COURT_CHARGE_ID,
    offenceCode: String = OFFENCE_CODE_1,
    offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
  ) = CourtEventChargeResponse(
    eventId = eventId,
    offenderCharge = OffenderChargeResponse(
      id = offenderChargeId,
      offence = OffenceResponse(
        offenceCode = offenceCode,
        statuteCode = "RR84",
        description = "Offence text",
      ),
      mostSeriousFlag = false,
      offenceDate = offenceStartDate,
      offenceEndDate = offenceStartDate.plusDays(1),
      resultCode1 = OffenceResultCodeResponse(
        code = OUTCOME_1,
        description = "Outcome text",
        dispositionCode = "F",
        chargeStatus = "A",
        conviction = true,
      ),
    ),
    offenceDate = offenceStartDate,
    offenceEndDate = offenceStartDate.plusDays(1),
    mostSeriousFlag = false,
    resultCode1 = OffenceResultCodeResponse(
      code = OUTCOME_1,
      description = "Outcome text",
      dispositionCode = "F",
      chargeStatus = "A",
      conviction = true,
    ),
  )

  fun dpsChargeResponse(offenceCode: String = OFFENCE_CODE_1, offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1)) = LegacyCharge(
    lifetimeUuid = UUID.fromString(DPS_COURT_CHARGE_ID),
    offenceCode = offenceCode,
    offenceStartDate = offenceStartDate,
    offenceEndDate = offenceStartDate.plusDays(1),
    nomisOutcomeCode = OUTCOME_1,
    courtCaseUuid = DPS_COURT_CASE_ID,
    prisonerId = OFFENDER_NO,
  )
}
