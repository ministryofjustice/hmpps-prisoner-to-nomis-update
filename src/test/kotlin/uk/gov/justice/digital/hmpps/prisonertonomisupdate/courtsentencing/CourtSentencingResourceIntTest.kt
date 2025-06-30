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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 7L
private const val NOMIS_COURT_CASE_2_ID = 8L
private const val DPS_COURT_CASE_ID = "4321"
private const val DPS_COURT_CASE_2_ID = "4321"
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_APPEARANCE_2_ID = "45591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_COURT_APPEARANCE_2_ID = 4L
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
private const val YEARS = 6
private const val MONTHS = 5
private const val WEEKS = 4
private const val DAYS = 3
private const val DPS_PERIOD_LENGTH_ID = "87591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_ID = "1c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val SENTENCE_CATEGORY = "2020"
private const val SENTENCE_CALC_TYPE = "ADIMP_ORA"
private const val NOMIS_SENTENCE_SEQ = 3L
private const val NOMIS_TERM_SEQ = 4L
private const val SENTENCE_TERM_TYPE = "IMP"

class CourtSentencingResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApi: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApi: CourtSentencingMappingApiMockServer

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
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
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
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenNomisId(
          id = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
        )
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCaseForReconciliation(
          DPS_COURT_CASE_ID,
          dpsCourtCaseResponse(
            active = true,
            appearances = listOf(
              dpsAppearanceResponse(
                appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_ID),
                appearanceDate = LocalDate.parse("2024-01-01"),
                charges = listOf(
                  dpsChargeResponse(offenceCode = OFFENCE_CODE_2),
                  dpsChargeResponse(
                    sentenceResponse = dpsSentenceResponse(
                      periodLengths = listOf(
                        dpsPeriodLengthResponse(),
                      ),
                    ),
                  ),
                ),
              ),
              dpsAppearanceResponse(
                appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_2_ID),
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
                id = NOMIS_COURT_APPEARANCE_2_ID,
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
            .jsonPath("mismatch.differences.size()").isEqualTo(2)
            // outcome on first appearance is different
            .jsonPath("mismatch.differences[0].property").isEqualTo("case.appearances[0].outcome")
            .jsonPath("mismatch.differences[0].dps").isEqualTo(4001)
            .jsonPath("mismatch.differences[0].nomis").isEqualTo(3001)
            // no nomis sentence found
            .jsonPath("mismatch.differences[1].property").isEqualTo("case.sentences")
            .jsonPath("mismatch.differences[1].nomis").isEqualTo(0)
            .jsonPath("mismatch.nomisCase.id").isEqualTo(NOMIS_COURT_CASE_ID.toString())
            .jsonPath("mismatch.dpsCase.id").isEqualTo(DPS_COURT_CASE_ID)
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
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenNomisId(
          id = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
        )

        courtSentencingMappingApi.stubGetCourtCaseMappingGivenNomisId(
          id = NOMIS_COURT_CASE_2_ID,
          dpsCourtCaseId = DPS_COURT_CASE_2_ID,
        )

        courtSentencingNomisApi.stubGetCourtCaseIdsByOffenderNo(
          offenderNo = OFFENDER_NO,
          response = listOf(NOMIS_COURT_CASE_ID, NOMIS_COURT_CASE_2_ID),
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
        courtSentencingNomisApi.stubCourtChargeCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID,
          OffenderChargeIdResponse(offenderChargeId = NOMIS_COURT_CHARGE_ID),
        )
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
          id = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        courtSentencingMappingApi.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)
        courtSentencingMappingApi.stubCreateCourtCharge()

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
          courtSentencingMappingApi.verify(
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
    appearances: List<ReconciliationCourtAppearance> = emptyList(),
    caseReferences: List<CaseReferenceLegacyData> = emptyList(),
  ) = ReconciliationCourtCase(
    courtCaseUuid = DPS_COURT_CASE_ID,
    prisonerId = OFFENDER_NO,
    active = true,
    appearances = appearances,
    courtCaseLegacyData = CourtCaseLegacyData(caseReferences),
    merged = false,
  )

  fun dpsAppearanceResponse(
    appearanceUuid: UUID = UUID.fromString(DPS_COURT_APPEARANCE_ID),
    outcome: String = OUTCOME_1,
    charges: List<ReconciliationCharge> = emptyList(),
    appearanceDate: LocalDate = LocalDate.of(2024, 1, 1),
  ) = ReconciliationCourtAppearance(
    appearanceUuid = appearanceUuid,
    // courtCaseUuid = DPS_COURT_CASE_ID,
    courtCode = PRISON_LEI,
    appearanceDate = appearanceDate,
    appearanceTime = "10:10",
    nomisOutcomeCode = outcome,
    charges = charges,
    // prisonerId = OFFENDER_NO,
    nextCourtAppearance = ReconciliationNextCourtAppearance(
      appearanceDate = LocalDate.of(2024, 2, 1),
      appearanceTime = "10:10",
      courtId = PRISON_MDI,
    ),
  )

  fun nomisCaseResponse(
    id: Long = NOMIS_COURT_CASE_ID,
    beginDate: LocalDate = LocalDate.of(2024, 1, 1),
    events: List<CourtEventResponse> = emptyList(),
    sentences: List<SentenceResponse> = emptyList(),
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
    sentences = sentences,
    sourceCombinedCaseIds = emptyList(),
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

  fun nomisOffenderChargeResponse(
    offenderChargeId: Long = NOMIS_COURT_CHARGE_ID,
    offenceCode: String = OFFENCE_CODE_1,
    offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
  ) = OffenderChargeResponse(
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
  )

  fun nomisSentenceResponse(
    eventId: Long,
    terms: List<SentenceTermResponse> = emptyList(),
    charges: List<OffenderChargeResponse> = emptyList(),
  ) = SentenceResponse(
    sentenceSeq = NOMIS_SENTENCE_SEQ,
    bookingId = NOMIS_BOOKING_ID,
    category = CodeDescription(SENTENCE_CATEGORY, "desc"),
    calculationType = CodeDescription(SENTENCE_CALC_TYPE, "desc"),
    startDate = LocalDate.of(2024, 1, 1),
    status = "A",
    sentenceTerms = terms,
    fineAmount = BigDecimal.TEN,
    missingCourtOffenderChargeIds = emptyList(),
    createdByUsername = "Q1251T",
    createdDateTime = LocalDateTime.now(),
    offenderCharges = charges,
    prisonId = PRISON_MDI,
    courtOrder = CourtOrderResponse(
      eventId = eventId,
      id = 1234,
      courtDate = LocalDate.now(),
      issuingCourt = "TFG",
      sentencePurposes = emptyList(),
      orderType = "type",
      orderStatus = "status",
    ),
  )

  fun nomisSentenceTermResponse(
    months: Int = MONTHS,
    termType: String = SENTENCE_TERM_TYPE,
    lifeSentence: Boolean = false,
  ) = SentenceTermResponse(
    years = YEARS,
    months = MONTHS,
    weeks = WEEKS,
    days = DAYS,
    sentenceTermType = CodeDescription(termType, "desc"),
    lifeSentenceFlag = lifeSentence,
    termSequence = NOMIS_TERM_SEQ,
    prisonId = PRISON_MDI,
    startDate = LocalDate.of(2023, 1, 1),
  )

  fun dpsChargeResponse(
    offenceCode: String = OFFENCE_CODE_1,
    offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
    sentenceResponse: ReconciliationSentence? = null,
  ) = ReconciliationCharge(
    chargeUuid = UUID.fromString(DPS_COURT_CHARGE_ID),
    offenceCode = offenceCode,
    offenceStartDate = offenceStartDate,
    offenceEndDate = offenceStartDate.plusDays(1),
    nomisOutcomeCode = OUTCOME_1,
    sentence = sentenceResponse,
    // = DPS_COURT_CASE_ID,
    // prisonerId = OFFENDER_NO,
  )

  fun dpsSentenceResponse(periodLengths: List<ReconciliationPeriodLength> = emptyList()) = ReconciliationSentence(
    sentenceUuid = UUID.fromString(DPS_SENTENCE_ID),
    sentenceCategory = SENTENCE_CATEGORY,
    sentenceCalcType = SENTENCE_CALC_TYPE,
    sentenceStartDate = LocalDate.of(2023, 1, 1),
    active = true,
    periodLengths = periodLengths,
    fineAmount = BigDecimal.TEN,
  )

  fun dpsPeriodLengthResponse() = ReconciliationPeriodLength(
    periodYears = YEARS,
    periodMonths = MONTHS,
    periodWeeks = WEEKS,
    periodDays = DAYS,
    sentenceTermCode = SENTENCE_TERM_TYPE,
    lifeSentence = false,
    periodLengthUuid = UUID.fromString(DPS_PERIOD_LENGTH_ID),
  )
}
