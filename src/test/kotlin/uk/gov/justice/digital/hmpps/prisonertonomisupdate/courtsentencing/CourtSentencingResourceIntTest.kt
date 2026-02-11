package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationChargeWithoutSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.countAllMessagesOnQueue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.readAtMost10RawMessages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchUpdateAndCreateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSentenceIdPair
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSentenceTermIdPair
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DpsCourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SimpleCourtSentencingIdPair
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtCaseCloneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ClonedCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseRepairResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceIdAndAdjustmentsCreated
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 7L
private const val DPS_COURT_CASE_ID = "4321"
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
  private val courtSentencingDpsApi = CourtSentencingApiExtension.courtSentencingApi

  @Autowired
  private lateinit var courtSentencingNomisApi: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApi: CourtSentencingMappingApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)

  @DisplayName("GET /court-sentencing/court-cases/dps-case-id/{dpsCaseId}/reconciliation")
  @Nested
  inner class ManualCaseReconciliationByDpsCaseId {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
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

          webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/dps-case-id/$DPS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
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

  @DisplayName("GET /prisoners/$OFFENDER_NO/court-sentencing/court-cases/nomis-case-id/{nomisCaseId}/reconciliation")
  @Nested
  inner class ManualCaseReconciliationByNomisCaseId {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
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

          webTestClient.get().uri("/prisoners/$OFFENDER_NO/court-sentencing/court-cases/nomis-case-id/$NOMIS_COURT_CASE_ID/reconciliation")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
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
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
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
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(urlEqualTo("/legacy/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-create-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(CourtChargeMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Charge`() {
        waitForAnyProcessingToComplete()
        nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID}/charges")))
      }

      @Test
      fun `will create a mapping between the two charges`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          courtSentencingMappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-charges"))
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

  @Nested
  @DisplayName("POST /prisoners/{offenderNo}/court-sentencing/court-case/{dpsCourtCaseId}/booking-repair")
  inner class CloneCourtCaseRepair {
    val dpsCourtCaseId = UUID.randomUUID().toString()
    val nomisCourtCaseId = 12345L
    val offenderNo = OFFENDER_NO

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/booking-repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/booking-repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/booking-repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPathWithTransientFailure {

      @BeforeEach
      fun setUp() {
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
          id = dpsCourtCaseId,
          nomisCourtCaseId = nomisCourtCaseId,
        )
        courtSentencingNomisApi.stubCloneCourtCase(
          offenderNo,
          nomisCourtCaseId,
          response = BookingCourtCaseCloneResponse(
            courtCases = listOf(
              ClonedCourtCaseResponse(
                sourceCourtCase = uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.nomisCaseResponse(
                  id = 101L,
                  events = listOf(nomisAppearanceResponse(id = 1001), nomisAppearanceResponse(id = 1002)),
                  sentences = listOf(
                    nomisSentenceResponse().copy(
                      bookingId = 1,
                      sentenceSeq = 10,
                      sentenceTerms = listOf(
                        nomisSentenceTermResponse().copy(termSequence = 1),
                        nomisSentenceTermResponse().copy(termSequence = 2),
                      ),
                    ),
                  ),
                ).copy(
                  bookingId = 1,
                  offenderCharges = listOf(
                    nomisOffenderChargeResponse(offenderChargeId = 1003),
                    nomisOffenderChargeResponse(offenderChargeId = 1004),
                  ),
                ),
                courtCase = nomisCaseResponse(
                  id = 201L,
                  events = listOf(nomisAppearanceResponse(id = 2001), nomisAppearanceResponse(id = 2002)),
                  sentences = listOf(
                    nomisSentenceResponse().copy(
                      bookingId = 2,
                      sentenceSeq = 20,
                      sentenceTerms = listOf(
                        nomisSentenceTermResponse().copy(termSequence = 21),
                        nomisSentenceTermResponse().copy(termSequence = 22),
                      ),
                    ),
                  ),
                ).copy(
                  bookingId = 2,
                  offenderCharges = listOf(
                    nomisOffenderChargeResponse(offenderChargeId = 2003),
                    nomisOffenderChargeResponse(offenderChargeId = 2004),
                  ),
                ),
              ),
            ),
            sentenceAdjustments = listOf(
              SentenceIdAndAdjustmentsCreated(
                sentenceId = SentenceId(
                  offenderBookingId = 2,
                  sentenceSequence = 20,
                ),
                adjustmentIds = listOf(20001, 20002),
              ),
            ),
          ),
        )

        courtSentencingMappingApi.stubUpdateAndCreateMappingsWithErrorFollowedBySuccess()

        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/booking-repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk

        waitForAnyProcessingToComplete("court-case-cloned-repair")
      }

      @Test
      fun `will create cloned telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-cloned-repair-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("court-case-cloned-repair"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["nomisCourtCaseIds"]).isEqualTo("101")
          },
          isNull(),
        )
      }

      @Test
      fun `will send message to nomis migration to sync court cases cloned`() {
        await untilAsserted {
          assertThat(fromNomisCourtSentencingQueue.countAllMessagesOnQueue()).isEqualTo(3)
        }
        val rawMessages = fromNomisCourtSentencingQueue.readAtMost10RawMessages()
        val sqsMessages: List<SQSMessage> = rawMessages.map { it.fromJson() }
        val sqsMessage = sqsMessages.first { it.Type == "courtsentencing.resync.case.booking" }
        assertThat(sqsMessage.Type).isEqualTo("courtsentencing.resync.case.booking")
        val request: OffenderCaseBookingResynchronisationEvent = sqsMessage.Message.fromJson()
        assertThat(request.offenderNo).isEqualTo(OFFENDER_NO)
        assertThat(request.caseIds).containsExactly(101L)
        assertThat(request.casesMoved).hasSize(1)
        assertThat(request.casesMoved[0].caseId).isEqualTo(201L)
        assertThat(request.casesMoved[0].sentences).hasSize(1)
        assertThat(request.casesMoved[0].sentences[0].sentenceSequence).isEqualTo(20)
        assertThat(request.fromBookingId).isEqualTo(1L)
        assertThat(request.toBookingId).isEqualTo(2L)
      }

      @Test
      fun `will send message to nomis migration to sync sentence adjustments cloned`() {
        await untilAsserted {
          assertThat(fromNomisCourtSentencingQueue.countAllMessagesOnQueue()).isEqualTo(3)
        }
        val rawMessages = fromNomisCourtSentencingQueue.readAtMost10RawMessages()
        val sqsMessages: List<SQSMessage> = rawMessages.map { it.fromJson() }
        val adjustmentMessages = sqsMessages.filter { it.Type == "courtsentencing.resync.sentence-adjustments" }

        assertThat(adjustmentMessages).hasSize(2)

        val requests: List<SyncSentenceAdjustment> = adjustmentMessages.map { it.Message.fromJson() }

        with(requests[0]) {
          assertThat(offenderNo).isEqualTo(OFFENDER_NO)
          assertThat(sentences).hasSize(1)
          assertThat(sentences[0].sentenceId.offenderBookingId).isEqualTo(2L)
          assertThat(sentences[0].sentenceId.sentenceSequence).isEqualTo(20)
          assertThat(sentences[0].adjustmentIds).hasSize(1)
          // either of the adjustments will be present
          assertThat(sentences[0].adjustmentIds[0] in listOf(20001L, 20002L)).isTrue
        }
        with(requests[1]) {
          assertThat(offenderNo).isEqualTo(OFFENDER_NO)
          assertThat(sentences).hasSize(1)
          assertThat(sentences[0].sentenceId.offenderBookingId).isEqualTo(2L)
          assertThat(sentences[0].sentenceId.sentenceSequence).isEqualTo(20)
          assertThat(sentences[0].adjustmentIds).hasSize(1)
          assertThat(sentences[0].adjustmentIds[0] in listOf(20001L, 20002L)).isTrue
        }
      }

      @Test
      fun `will call nomis api to clone case`() {
        courtSentencingNomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$offenderNo/sentencing/court-cases/clone/$nomisCourtCaseId")),
        )
      }

      @Test
      fun `will update for clones cases`() {
        val request: CourtCaseBatchUpdateAndCreateMappingDto = CourtSentencingMappingApiMockServer.getRequestBody(putRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases/update-create")))
        assertThat(request.mappingsToCreate.courtCases).isEmpty()
        assertThat(request.mappingsToCreate.courtAppearances).isEmpty()
        assertThat(request.mappingsToCreate.courtCharges).isEmpty()
        assertThat(request.mappingsToCreate.sentences).isEmpty()
        assertThat(request.mappingsToCreate.sentenceTerms).isEmpty()

        assertThat(request.mappingsToUpdate.courtCases).containsExactly(
          SimpleCourtSentencingIdPair(
            fromNomisId = 101,
            toNomisId = 201,
          ),
        )
        assertThat(request.mappingsToUpdate.courtAppearances).containsExactlyInAnyOrder(
          SimpleCourtSentencingIdPair(fromNomisId = 1001, toNomisId = 2001),
          SimpleCourtSentencingIdPair(fromNomisId = 1002, toNomisId = 2002),
        )
        assertThat(request.mappingsToUpdate.courtCharges).containsExactlyInAnyOrder(
          SimpleCourtSentencingIdPair(fromNomisId = 1003, toNomisId = 2003),
          SimpleCourtSentencingIdPair(fromNomisId = 1004, toNomisId = 2004),
        )
        assertThat(request.mappingsToUpdate.sentences).containsExactlyInAnyOrder(
          CourtSentenceIdPair(
            fromNomisId = MappingSentenceId(nomisBookingId = 1, nomisSequence = 10),
            toNomisId = MappingSentenceId(nomisBookingId = 2, nomisSequence = 20),
          ),
        )

        assertThat(request.mappingsToUpdate.sentenceTerms).containsExactlyInAnyOrder(
          CourtSentenceTermIdPair(
            fromNomisId = SentenceTermId(
              MappingSentenceId(nomisBookingId = 1, nomisSequence = 10),
              nomisSequence = 1,
            ),
            SentenceTermId(
              nomisSentenceId = MappingSentenceId(nomisBookingId = 2, nomisSequence = 20),
              nomisSequence = 21,
            ),
          ),
          CourtSentenceTermIdPair(
            fromNomisId = SentenceTermId(
              MappingSentenceId(nomisBookingId = 1, nomisSequence = 10),
              nomisSequence = 2,
            ),
            SentenceTermId(
              nomisSentenceId = MappingSentenceId(nomisBookingId = 2, nomisSequence = 20),
              nomisSequence = 22,
            ),
          ),
        )
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
          id = dpsCourtCaseId,
          nomisCourtCaseId = nomisCourtCaseId,
        )
        courtSentencingNomisApi.stubCloneCourtCase(
          offenderNo,
          nomisCourtCaseId,
          response = BookingCourtCaseCloneResponse(
            courtCases = listOf(
              ClonedCourtCaseResponse(
                sourceCourtCase = uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.nomisCaseResponse(
                  id = 101L,
                  events = listOf(nomisAppearanceResponse(id = 1001), nomisAppearanceResponse(id = 1002)),
                  sentences = listOf(
                    nomisSentenceResponse().copy(
                      bookingId = 1,
                      sentenceSeq = 10,
                      sentenceTerms = listOf(
                        nomisSentenceTermResponse().copy(termSequence = 1),
                        nomisSentenceTermResponse().copy(termSequence = 2),
                      ),
                    ),
                  ),
                ).copy(
                  bookingId = 1,
                  offenderCharges = listOf(
                    nomisOffenderChargeResponse(offenderChargeId = 1003),
                    nomisOffenderChargeResponse(offenderChargeId = 1004),
                  ),
                ),
                courtCase = nomisCaseResponse(
                  id = 201L,
                  events = listOf(nomisAppearanceResponse(id = 2001), nomisAppearanceResponse(id = 2002)),
                  sentences = listOf(
                    nomisSentenceResponse().copy(
                      bookingId = 2,
                      sentenceSeq = 20,
                      sentenceTerms = listOf(
                        nomisSentenceTermResponse().copy(termSequence = 21),
                        nomisSentenceTermResponse().copy(termSequence = 22),
                      ),
                    ),
                  ),
                ).copy(
                  bookingId = 2,
                  offenderCharges = listOf(
                    nomisOffenderChargeResponse(offenderChargeId = 2003),
                    nomisOffenderChargeResponse(offenderChargeId = 2004),
                  ),
                ),
              ),
            ),
            sentenceAdjustments = listOf(
              SentenceIdAndAdjustmentsCreated(
                sentenceId = SentenceId(
                  offenderBookingId = 2,
                  sentenceSequence = 20,
                ),
                adjustmentIds = listOf(20001, 20002),
              ),
            ),
          ),
        )

        courtSentencingMappingApi.stubUpdateAndCreateMappings()

        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/booking-repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will create cloned telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-cloned-repair"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["nomisCourtCaseIds"]).isEqualTo("101")
          },
          isNull(),
        )
      }

      @Test
      fun `will send message to nomis migration to sync court cases cloned`() {
        await untilAsserted {
          assertThat(fromNomisCourtSentencingQueue.countAllMessagesOnQueue()).isEqualTo(3)
        }
        val rawMessages = fromNomisCourtSentencingQueue.readAtMost10RawMessages()
        val sqsMessages: List<SQSMessage> = rawMessages.map { it.fromJson() }
        val sqsMessage = sqsMessages.first { it.Type == "courtsentencing.resync.case.booking" }
        assertThat(sqsMessage.Type).isEqualTo("courtsentencing.resync.case.booking")
        val request: OffenderCaseBookingResynchronisationEvent = sqsMessage.Message.fromJson()
        assertThat(request.offenderNo).isEqualTo(OFFENDER_NO)
        assertThat(request.caseIds).containsExactly(101L)
        assertThat(request.casesMoved).hasSize(1)
        assertThat(request.casesMoved[0].caseId).isEqualTo(201L)
        assertThat(request.casesMoved[0].sentences).hasSize(1)
        assertThat(request.casesMoved[0].sentences[0].sentenceSequence).isEqualTo(20)
        assertThat(request.fromBookingId).isEqualTo(1L)
        assertThat(request.toBookingId).isEqualTo(2L)
      }

      @Test
      fun `will send message to nomis migration to sync sentence adjustments cloned`() {
        await untilAsserted {
          assertThat(fromNomisCourtSentencingQueue.countAllMessagesOnQueue()).isEqualTo(3)
        }
        val rawMessages = fromNomisCourtSentencingQueue.readAtMost10RawMessages()
        val sqsMessages: List<SQSMessage> = rawMessages.map { it.fromJson() }
        val adjustmentMessages = sqsMessages.filter { it.Type == "courtsentencing.resync.sentence-adjustments" }

        assertThat(adjustmentMessages).hasSize(2)

        val requests: List<SyncSentenceAdjustment> = adjustmentMessages.map { it.Message.fromJson() }

        with(requests[0]) {
          assertThat(offenderNo).isEqualTo(OFFENDER_NO)
          assertThat(sentences).hasSize(1)
          assertThat(sentences[0].sentenceId.offenderBookingId).isEqualTo(2L)
          assertThat(sentences[0].sentenceId.sentenceSequence).isEqualTo(20)
          assertThat(sentences[0].adjustmentIds).hasSize(1)
          // either of the adjustments will be present
          assertThat(sentences[0].adjustmentIds[0] in listOf(20001L, 20002L)).isTrue
        }
        with(requests[1]) {
          assertThat(offenderNo).isEqualTo(OFFENDER_NO)
          assertThat(sentences).hasSize(1)
          assertThat(sentences[0].sentenceId.offenderBookingId).isEqualTo(2L)
          assertThat(sentences[0].sentenceId.sentenceSequence).isEqualTo(20)
          assertThat(sentences[0].adjustmentIds).hasSize(1)
          assertThat(sentences[0].adjustmentIds[0] in listOf(20001L, 20002L)).isTrue
        }
      }

      @Test
      fun `will call nomis api to clone case`() {
        courtSentencingNomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$offenderNo/sentencing/court-cases/clone/$nomisCourtCaseId")),
        )
      }

      @Test
      fun `will update for clones cases`() {
        val request: CourtCaseBatchUpdateAndCreateMappingDto = CourtSentencingMappingApiMockServer.getRequestBody(putRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases/update-create")))
        assertThat(request.mappingsToCreate.courtCases).isEmpty()
        assertThat(request.mappingsToCreate.courtAppearances).isEmpty()
        assertThat(request.mappingsToCreate.courtCharges).isEmpty()
        assertThat(request.mappingsToCreate.sentences).isEmpty()
        assertThat(request.mappingsToCreate.sentenceTerms).isEmpty()

        assertThat(request.mappingsToUpdate.courtCases).containsExactly(
          SimpleCourtSentencingIdPair(
            fromNomisId = 101,
            toNomisId = 201,
          ),
        )
        assertThat(request.mappingsToUpdate.courtAppearances).containsExactlyInAnyOrder(
          SimpleCourtSentencingIdPair(fromNomisId = 1001, toNomisId = 2001),
          SimpleCourtSentencingIdPair(fromNomisId = 1002, toNomisId = 2002),
        )
        assertThat(request.mappingsToUpdate.courtCharges).containsExactlyInAnyOrder(
          SimpleCourtSentencingIdPair(fromNomisId = 1003, toNomisId = 2003),
          SimpleCourtSentencingIdPair(fromNomisId = 1004, toNomisId = 2004),
        )
        assertThat(request.mappingsToUpdate.sentences).containsExactlyInAnyOrder(
          CourtSentenceIdPair(
            fromNomisId = MappingSentenceId(nomisBookingId = 1, nomisSequence = 10),
            toNomisId = MappingSentenceId(nomisBookingId = 2, nomisSequence = 20),
          ),
        )

        assertThat(request.mappingsToUpdate.sentenceTerms).containsExactlyInAnyOrder(
          CourtSentenceTermIdPair(
            fromNomisId = SentenceTermId(
              MappingSentenceId(nomisBookingId = 1, nomisSequence = 10),
              nomisSequence = 1,
            ),
            SentenceTermId(
              nomisSentenceId = MappingSentenceId(nomisBookingId = 2, nomisSequence = 20),
              nomisSequence = 21,
            ),
          ),
          CourtSentenceTermIdPair(
            fromNomisId = SentenceTermId(
              MappingSentenceId(nomisBookingId = 1, nomisSequence = 10),
              nomisSequence = 2,
            ),
            SentenceTermId(
              nomisSentenceId = MappingSentenceId(nomisBookingId = 2, nomisSequence = 20),
              nomisSequence = 22,
            ),
          ),
        )
      }
    }
  }

  @Nested
  @DisplayName("POST /prisoners/{offenderNo}/court-sentencing/court-case/{courtCaseId}/repair")
  inner class RepairCourtCaseInNomis {
    val dpsCourtCaseId = UUID.randomUUID().toString()
    val nomisCourtCaseId = 12345L
    val offenderNo = OFFENDER_NO

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class BadInputPath {
      @Nested
      inner class CaseHasASentence {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(dpsCourtCaseId, nomisCourtCaseId)
          courtSentencingDpsApi.stubGetCourtCaseForReconciliation(
            courtCaseId = dpsCourtCaseId,
            courtCaseResponse = reconciliationCourtCase(appearances = listOf(reconciliationCourtAppearance(charges = listOf(reconciliationCharge(sentenceResponse = reconciliationSentence()))))),
          )
        }

        @Test
        fun `will return conflict error when court case has a sentence`() {
          webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/repair")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isEqualTo(409)
        }
      }

      @Nested
      inner class CaseReferenceNotFound {

        @Nested
        inner class UsingDPSCaseId {

          @BeforeEach
          fun setUp() {
            courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(dpsCourtCaseId, HttpStatus.NOT_FOUND)
          }

          @Test
          fun `will return 400 error`() {
            webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/repair")
              .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
              .exchange()
              .expectStatus().isBadRequest
          }
        }

        @Nested
        inner class UsingNOMISCaseId {

          @BeforeEach
          fun setUp() {
            courtSentencingMappingApi.stubGetCourtCaseMappingGivenNomisId(nomisCourtCaseId, HttpStatus.NOT_FOUND)
          }

          @Test
          fun `will return 400 error`() {
            webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$nomisCourtCaseId/repair")
              .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
              .exchange()
              .expectStatus().isBadRequest
          }
        }
      }
    }

    @Nested
    inner class HappyPath {
      val newNomisCaseId = 1234L
      val dpsAppearanceId: UUID = UUID.randomUUID()
      val dpsChargeId: UUID = UUID.randomUUID()
      val nomisAppearanceId = 23456L
      val nomisChargeId = 34567L

      @BeforeEach
      fun setUp() {
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(dpsCourtCaseId, nomisCourtCaseId)
        courtSentencingDpsApi.stubGetCourtCaseForReconciliation(
          courtCaseId = dpsCourtCaseId,
          courtCaseResponse = reconciliationCourtCase(
            caseReferences = listOf(
              CaseReferenceLegacyData(
                "ABC4999",
                updatedDate = LocalDateTime.now(),
              ),
            ),
            appearances = listOf(reconciliationCourtAppearance(appearanceUuid = dpsAppearanceId, charges = listOf(reconciliationChargeWithoutSentence().copy(chargeUuid = dpsChargeId))).copy(courtCode = "MDI")),
          ),
        )
        courtSentencingMappingApi.stubDeleteMappingsByDpsIds()
        courtSentencingNomisApi.stubRepairCourtCase(
          offenderNo,
          nomisCourtCaseId,
          response = CourtCaseRepairResponse(
            caseId = newNomisCaseId,
            courtAppearanceIds = listOf(nomisAppearanceId),
            offenderChargeIds = listOf(nomisChargeId),
            bookingId = 1234L,
          ),
        )
        courtSentencingMappingApi.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$offenderNo/court-sentencing/court-case/$dpsCourtCaseId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will call NOMIS API to repair the case`() {
        val nomisRepairRequest: CourtCaseRepairRequest = nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/prisoners/$offenderNo/sentencing/court-cases/$nomisCourtCaseId/repair")), jsonMapper)

        assertThat(nomisRepairRequest.courtId).isEqualTo("MDI")
        assertThat(nomisRepairRequest.courtAppearances).hasSize(1)
        assertThat(nomisRepairRequest.offenderCharges).hasSize(1)
        assertThat(nomisRepairRequest.courtAppearances[0].courtEventCharges).hasSize(1)
        assertThat(nomisRepairRequest.caseReferences?.caseIdentifiers).hasSize(1)
      }

      @Test
      fun `will call the mapping API to replace mappings`() {
        val mappingReplaceRequest: CourtCaseBatchMappingDto = CourtSentencingMappingApiMockServer.Companion.getRequestBody(putRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases/replace")))

        assertThat(mappingReplaceRequest.courtCases).hasSize(1)
        with(mappingReplaceRequest.courtCases[0]) {
          assertThat(dpsCourtCaseId).isEqualTo(dpsCourtCaseId)
          assertThat(nomisCourtCaseId).isEqualTo(newNomisCaseId)
        }
        assertThat(mappingReplaceRequest.courtAppearances).hasSize(1)
        with(mappingReplaceRequest.courtAppearances[0]) {
          assertThat(dpsCourtAppearanceId).isEqualTo(dpsAppearanceId.toString())
          assertThat(nomisCourtAppearanceId).isEqualTo(nomisAppearanceId)
        }
        assertThat(mappingReplaceRequest.courtCharges).hasSize(1)
        with(mappingReplaceRequest.courtCharges[0]) {
          assertThat(dpsCourtChargeId).isEqualTo(dpsChargeId.toString())
          assertThat(nomisCourtChargeId).isEqualTo(nomisChargeId)
        }
      }

      @Test
      fun `will call the mapping API to delete existing mappings`() {
        val mappingDeleteRequest: DpsCourtCaseBatchMappingDto = CourtSentencingMappingApiMockServer.Companion.getRequestBody(postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases/delete-by-dps-ids")))

        assertThat(mappingDeleteRequest.courtCases).containsExactly(dpsCourtCaseId)
        assertThat(mappingDeleteRequest.courtAppearances).containsExactly(dpsAppearanceId.toString())
        assertThat(mappingDeleteRequest.courtCharges).containsExactly(dpsChargeId.toString())
      }

      @Test
      fun `will track telemetry for success`() {
        verify(telemetryClient).trackEvent(
          eq("court-sentencing-repair-court-case"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(dpsCourtCaseId)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(nomisCourtCaseId.toString())
            assertThat(it["newNomisCourtCaseId"]).isEqualTo(newNomisCaseId.toString())
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("POST /prisoners/{offenderNo}/court-sentencing/dps-court-case/{courtCaseId}/dps-appearance/{appearanceId}/dps-sentence/{sentenceId}/repair")
  @Nested
  inner class SentenceInsertedRepair {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
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
        webTestClient.post().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
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
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = DPS_COURT_CASE_ID,
          chargeUuid = DPS_COURT_CHARGE_ID,
        )
        courtSentencingNomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID,
          response = CreateSentenceResponse(sentenceSeq = NOMIS_SENTENCE_SEQ, bookingId = NOMIS_BOOKING_ID),
        )
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
          id = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        courtSentencingMappingApi.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        courtSentencingMappingApi.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        courtSentencingMappingApi.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        courtSentencingMappingApi.stubCreateSentence()

        webTestClient.post().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete(2)
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(urlEqualTo("/legacy/sentence/${DPS_SENTENCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete(2)

        verify(telemetryClient).trackEvent(
          eq("sentence-create-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["mappingType"]).isEqualTo(CourtChargeMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the sentence`() {
        waitForAnyProcessingToComplete(2)
        nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID/sentences")))
      }
    }
  }

  @DisplayName("PUT /prisoners/{offenderNo}/court-sentencing/dps-court-case/{courtCaseId}/dps-appearance/{appearanceId}/dps-sentence/{sentenceId}/repair")
  @Nested
  inner class SentenceUpdatedRepair {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
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
        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
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
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = DPS_COURT_CASE_ID,
          chargeUuid = DPS_COURT_CHARGE_ID,
        )
        courtSentencingNomisApi.stubSentenceUpdate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
        )
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
          id = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        courtSentencingMappingApi.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        courtSentencingMappingApi.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        courtSentencingMappingApi.stubGetSentenceMappingGivenDpsId(id = DPS_SENTENCE_ID, nomisSentenceSequence = NOMIS_SENTENCE_SEQ, nomisBookingId = NOMIS_BOOKING_ID)

        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-sentence/$DPS_SENTENCE_ID/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete(2)
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(urlEqualTo("/legacy/sentence/${DPS_SENTENCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete(2)

        verify(telemetryClient).trackEvent(
          eq("sentence-updated-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the sentence`() {
        waitForAnyProcessingToComplete(2)
        nomisApi.verify(putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID/sentences/$NOMIS_SENTENCE_SEQ")))
      }
    }
  }

  @DisplayName("PUT /prisoners/{offenderNo}/court-sentencing/dps-court-case/{courtCaseId}/dps-appearance/{appearanceId}/dps-charge/{chargeId}/repair")
  @Nested
  inner class ChargeUpdatedRepair {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-charge/$DPS_COURT_CHARGE_ID/repair")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-charge/$DPS_COURT_CHARGE_ID/repair")
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
        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-charge/$DPS_COURT_CHARGE_ID/repair")
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
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtChargeByAppearance(
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = DPS_COURT_CASE_ID,
          courtChargeId = DPS_COURT_CHARGE_ID,
        )
        courtSentencingNomisApi.stubCourtChargeUpdate(
          offenderNo = OFFENDER_NO,
          courtCaseId = NOMIS_COURT_CASE_ID,
          offenderChargeId = NOMIS_COURT_CHARGE_ID,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        courtSentencingMappingApi.stubGetCourtCaseMappingGivenDpsId(
          id = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        courtSentencingMappingApi.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        courtSentencingMappingApi.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        webTestClient.put().uri("/prisoners/$OFFENDER_NO/court-sentencing/dps-court-case/$DPS_COURT_CASE_ID/dps-appearance/$DPS_COURT_APPEARANCE_ID/dps-charge/$DPS_COURT_CHARGE_ID/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete(2)
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(urlEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete(2)

        verify(telemetryClient).trackEvent(
          eq("charge-updated-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the charge`() {
        waitForAnyProcessingToComplete(2)
        nomisApi.verify(putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID/court-appearances/$NOMIS_COURT_APPEARANCE_ID/charges/$NOMIS_COURT_CHARGE_ID")))
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
    status = if (active) ReconciliationCourtCase.Status.ACTIVE else ReconciliationCourtCase.Status.INACTIVE,
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
    appearanceTypeUuid = UUID.fromString("1da09b6e-55cb-4838-a157-ee6944f2094c"),
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
      createdByUsername = "msmith",
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
    createdByUsername = "msmith",
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
    createdByUsername = "msmith",
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
    createdByUsername = "msmith",
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
