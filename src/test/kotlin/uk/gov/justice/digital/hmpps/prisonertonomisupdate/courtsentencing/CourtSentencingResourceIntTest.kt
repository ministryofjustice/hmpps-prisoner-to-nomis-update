package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.AppearanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.Charge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ChargeOutcome
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtAppearanceOutcome
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.NextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 7L
private const val DPS_COURT_CASE_ID = "4321"
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_APPEARANCE_2_ID = "1c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_COURT_APPEARANCE_2_ID = 4L
private const val NOMIS_NEXT_COURT_APPEARANCE_ID = 9L
private const val NOMIS_COURT_CHARGE_ID = 11L
private const val NOMIS_COURT_CHARGE_2_ID = 12L
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_2_ID = "9996aa44-642a-484a-a967-2d17b5c9c5a1"
private const val OFFENDER_NO = "AB12345"
private const val DONCASTER_COURT_CODE = "DRBYYC"
private const val PRISON_MDI = "MDI"
private const val PRISON_LEI = "LEI"
private const val CASE_REFERENCE = "ABC4999"
private const val OFFENCE_CODE_1 = "TR11017"
private const val OFFENCE_CODE_2 = "PR52028A"
private const val OUTCOME_1 = "4001"
private const val OUTCOME_2 = "3001"

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
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation(
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
          nomisApi.stubGetCourtCase(
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
          nomisApi.stubGetCourtCase(
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
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation(
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
          nomisApi.stubGetCourtCase(
            caseId = NOMIS_COURT_CASE_ID,
            beginDate = LocalDate.parse("2024-01-01"),
            courtEvents = listOf(
              nomisAppearanceResponse(
                eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
                outcome = OffenceResultCodeResponse(code = OUTCOME_2, description = "Outcome text", dispositionCode = "F", chargeStatus = "A"),
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
          nomisApi.stubGetCourtCase(
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
          nomisApi.stubGetCourtCase(
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
          nomisApi.stubGetCourtCase(
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

  fun dpsCourtCaseResponse(active: Boolean, appearances: List<CourtAppearance> = emptyList()) = CourtCase(
    courtCaseUuid = DPS_COURT_CASE_ID,
    prisonerId = OFFENDER_NO,
    status = CourtCase.Status.ACTIVE,
    appearances = appearances,
    draftAppearances = emptyList(),
  )

  fun dpsAppearanceResponse(
    outcome: CourtAppearanceOutcome = dpsAppearanceOutcomeResponse(),
    charges: List<Charge> = emptyList(),
    appearanceDate: LocalDate = LocalDate.of(2024, 1, 1),
  ) = CourtAppearance(
    lifetimeUuid = UUID.fromString(DPS_COURT_APPEARANCE_ID),
    courtCode = PRISON_LEI,
    appearanceDate = appearanceDate,
    outcome = outcome,
    charges = charges,

    appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_ID),
    warrantType = "WARRANT",
    nextCourtAppearance = NextCourtAppearance(
      appearanceDate = LocalDate.of(2024, 2, 1),
      appearanceTime = "10:10",
      courtCode = PRISON_MDI,
      appearanceType = AppearanceType(UUID.randomUUID(), "Court Appearance", displayOrder = 1),
    ),
  )

  fun nomisAppearanceResponse(
    id: Long = NOMIS_COURT_APPEARANCE_ID,
    outcome: OffenceResultCodeResponse = OffenceResultCodeResponse(code = OUTCOME_1, description = "Outcome text", dispositionCode = "F", chargeStatus = "A"),
    eventDateTime: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
    nextEventDateTime: String = LocalDateTime.of(2024, 2, 1, 10, 10, 0).toString(),
    charges: List<CourtEventChargeResponse> = emptyList(),
  ) = CourtEventResponse(
    id = id,
    offenderNo = OFFENDER_NO,
    caseId = NOMIS_COURT_CASE_ID,
    courtId = PRISON_LEI,
    courtEventCharges = charges,
    createdDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    createdByUsername = "Q1251T",
    courtEventType = CodeDescription("CRT", "Court Appearance"),
    outcomeReasonCode = outcome,
    eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
    eventDateTime = eventDateTime.toString(),
    courtOrders = emptyList(),
    nextEventDateTime = nextEventDateTime,
  )

  fun nomisChargeResponse(
    eventId: Long = NOMIS_COURT_APPEARANCE_ID,
    offenderChargeId: Long = NOMIS_COURT_CHARGE_ID,
    offenceCode: String = OFFENCE_CODE_1,
    offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
  ) =
    CourtEventChargeResponse(
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
        resultCode1 = OffenceResultCodeResponse(code = OUTCOME_1, description = "Outcome text", dispositionCode = "F", chargeStatus = "A"),
      ),
      offenceDate = offenceStartDate,
      offenceEndDate = offenceStartDate.plusDays(1),
      mostSeriousFlag = false,
      resultCode1 = OffenceResultCodeResponse(code = OUTCOME_1, description = "Outcome text", dispositionCode = "F", chargeStatus = "A"),
    )

  fun dpsChargeResponse(offenceCode: String = OFFENCE_CODE_1, offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1)) = Charge(
    lifetimeUuid = UUID.fromString(DPS_COURT_CHARGE_ID),
    offenceCode = offenceCode,
    offenceStartDate = offenceStartDate,
    offenceEndDate = offenceStartDate.plusDays(1),
    outcome = ChargeOutcome(
      displayOrder = 1,
      isSubList = false,
      nomisCode = OUTCOME_1,
      outcomeName = "OUTCOME_NAME",
      outcomeType = "OUTCOME_TYPE",
      outcomeUuid = UUID.randomUUID(),
    ),
    // not of interest but required for the model
    chargeUuid = UUID.randomUUID(),
  )

  fun dpsAppearanceOutcomeResponse(nomisCode: String = OUTCOME_1) = CourtAppearanceOutcome(
    displayOrder = 1,
    isSubList = false,
    nomisCode = nomisCode,
    outcomeName = "OUTCOME_NAME",
    outcomeType = "OUTCOME_TYPE",
    outcomeUuid = UUID.randomUUID(),
    relatedChargeOutcomeUuid = UUID.randomUUID(),
  )
}
