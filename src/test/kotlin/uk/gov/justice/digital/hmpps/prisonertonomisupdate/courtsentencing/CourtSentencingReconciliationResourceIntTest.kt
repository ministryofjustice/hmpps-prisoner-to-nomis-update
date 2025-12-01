package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCaseUuids
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

private const val DPS_CASE_ID = "11111111-1111-1111-1111-111111111111"

private const val OFFENDER_NO = "A0001TZ"
private const val OFFENDER_NO_2 = "A7701TZ"
private const val BOOKING_ID = 123456L
private const val BOOKING_ID_2 = 888888L

private const val DPS_CASE_2_ID = "21111111-1111-1111-1111-111111111111"
private const val DPS_CASE_3_ID = "31111111-1111-1111-1111-222222222222"

private const val NOMIS_CASE_ID = 1L
private const val NOMIS_CASE_2_ID = 2L
private const val NOMIS_CASE_3_ID = 3L

class CourtSentencingReconciliationResourceIntTest(
  @Autowired private val courtSentencingReconciliationService: CourtSentencingReconciliationService,
  @Autowired private val nomisApi: CourtSentencingNomisApiMockServer,
  @Autowired private val courtSentencingMappingApi: CourtSentencingMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = CourtSentencingApiExtension.courtSentencingApi
  private val nomisPrisonerApi = NomisApiExtension.Companion.nomisApi

  @DisplayName("Court cases prisoner reconciliation report")
  @Nested
  inner class GenerateCourtCasePrisonerReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisPrisonerApi.stuGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 4,
          prisonerIds = (NOMIS_CASE_ID..5).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      stubCases(
        OFFENDER_NO,
        listOf(nomisCaseResponse().copy(id = NOMIS_CASE_ID)),
        listOf(dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_ID)),
      )
      stubCases("A0002TZ", emptyList(), emptyList())
      stubCases(
        "A0003TZ",
        listOf(nomisCaseResponse().copy(id = 2L, bookingId = BOOKING_ID_2, caseStatus = CodeDescription("I", "Inactive"))),
        listOf(dpsCourtCaseResponse().copy(courtCaseUuid = "11111111-1111-1111-1111-111111111112", active = true)),
      )
      stubCases("A0004TZ", emptyList(), emptyList())

      stubCases("A0005TZ", listOf(nomisCaseResponse().copy(id = 5)), emptyList())
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      courtSentencingReconciliationService.generateCourtCasePrisonerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() = runTest {
      courtSentencingReconciliationService.generateCourtCasePrisonerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "5")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there a difference in the number of cases`() = runTest {
      courtSentencingReconciliationService.generateCourtCasePrisonerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0005TZ",
            "dpsCaseCount" to "0",
            "nomisCaseCount" to "1",
            "caseCountMismatch" to "true",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a difference in the  DPS record`() = runTest {
      courtSentencingReconciliationService.generateCourtCasePrisonerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "dpsCaseId" to "11111111-1111-1111-1111-111111111112",
            "nomisBookingId" to "$BOOKING_ID_2",
            "nomisCaseId" to "2",
            "mismatchCount" to "1",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("court-case-prisoner-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }

  @DisplayName("Court cases active prisoner reconciliation report")
  @Nested
  inner class GenerateCourtCaseActivePrisonerReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisPrisonerApi.stuGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 4,
          prisonerIds = (NOMIS_CASE_ID..4).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      stubCases(
        OFFENDER_NO,
        listOf(nomisCaseResponse().copy(id = NOMIS_CASE_ID)),
        listOf(dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_ID)),
      )
      stubCases("A0002TZ", emptyList(), emptyList())
      stubCases(
        "A0003TZ",
        listOf(nomisCaseResponse().copy(id = 2L, bookingId = BOOKING_ID_2, caseStatus = CodeDescription("I", "Inactive"))),
        listOf(dpsCourtCaseResponse().copy(courtCaseUuid = "11111111-1111-1111-1111-111111111112", active = true)),
      )
      stubCases("A0004TZ", emptyList(), emptyList())
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      courtSentencingReconciliationService.generateCourtCaseActivePrisonerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("court-case-active-prisoner-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() = runTest {
      courtSentencingReconciliationService.generateCourtCaseActivePrisonerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-active-prisoner-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "4")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a difference in the  DPS record`() = runTest {
      courtSentencingReconciliationService.generateCourtCaseActivePrisonerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-active-prisoner-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "dpsCaseId" to "11111111-1111-1111-1111-111111111112",
            "nomisBookingId" to "$BOOKING_ID_2",
            "nomisCaseId" to "2",
            "mismatchCount" to "1",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("court-case-active-prisoner-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }

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
        stubCases(
          OFFENDER_NO,
          listOf(
            nomisCaseResponse().copy(id = NOMIS_CASE_ID, courtEvents = listOf(nomisAppearanceResponse(), nomisAppearanceResponse())),
            nomisCaseResponse().copy(id = NOMIS_CASE_2_ID),
          ),
          listOf(
            dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_ID),
            dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_2_ID),
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch when sentence number is different`() {
          webTestClient.get().uri("prisoners/$OFFENDER_NO/court-sentencing/court-cases/reconciliation")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("$[0].mismatch.differences.size()").isEqualTo("1")
            .jsonPath("$[0].offenderNo").isEqualTo(OFFENDER_NO)
            // outcome on first appearance is different
            .jsonPath("$[0]mismatch.differences[0].property").isEqualTo("case.appearances")
            .jsonPath("$[0]mismatch.differences[0].dps").isEqualTo(1)
            .jsonPath("$[0].mismatch.differences[0].nomis").isEqualTo(2)
            .jsonPath("$[0].mismatch.nomisCase.id").isEqualTo(NOMIS_CASE_ID.toString())
            .jsonPath("$[0].mismatch.dpsCase.id").isEqualTo(DPS_CASE_ID)
        }
      }
    }
  }

  @Nested
  inner class ManualCaseReconciliationByOffenderIdList {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/court-sentencing/court-cases/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .body(BodyInserters.fromValue(listOf(OFFENDER_NO, OFFENDER_NO_2)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/court-sentencing/court-cases/reconciliation")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .body(BodyInserters.fromValue(listOf(OFFENDER_NO, OFFENDER_NO_2)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/court-sentencing/court-cases/reconciliation")
          .body(BodyInserters.fromValue(listOf(OFFENDER_NO, OFFENDER_NO_2)))
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        stubCases(
          OFFENDER_NO,
          listOf(
            nomisCaseResponse().copy(id = NOMIS_CASE_ID, offenderNo = OFFENDER_NO, courtEvents = listOf(nomisAppearanceResponse(), nomisAppearanceResponse())),
            nomisCaseResponse().copy(id = NOMIS_CASE_2_ID, offenderNo = OFFENDER_NO_2),
          ),
          listOf(
            dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_ID),
            dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_2_ID),
          ),
        )
        stubCases(
          OFFENDER_NO_2,
          listOf(
            nomisCaseResponse().copy(id = NOMIS_CASE_ID, courtEvents = listOf(nomisAppearanceResponse(), nomisAppearanceResponse())),
            nomisCaseResponse().copy(id = NOMIS_CASE_2_ID),
          ),
          listOf(
            dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_ID),
            dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_2_ID),
          ),
        )
      }

      @Nested
      inner class MismatchFound {
        @Test
        fun `will return a mismatch when sentence number is different`() {
          webTestClient.post().uri("prisoners/court-sentencing/court-cases/reconciliation")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(listOf(OFFENDER_NO, OFFENDER_NO_2)))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("$[0][0].mismatch.differences.size()").isEqualTo("1")
            .jsonPath("$[1][0].mismatch.differences.size()").isEqualTo("1")
            .jsonPath("$[0][0].offenderNo").isEqualTo(OFFENDER_NO)
            .jsonPath("$[0][0].nomisBookingId").isEqualTo(BOOKING_ID)
            .jsonPath("$[1][0].offenderNo").isEqualTo(OFFENDER_NO_2)
        }
      }
    }
  }

  private fun stubCases(offenderNo: String, nomisCases: List<CourtCaseResponse>, dpsCases: List<ReconciliationCourtCase>) {
    nomisApi.stubGetCourtCaseIdsByOffenderNo(offenderNo, response = nomisCases.map { it.id })
    dpsApi.stubGetCourtCaseIdsForReconciliation(offenderNo, LegacyCourtCaseUuids(dpsCases.map { it.courtCaseUuid }))
    if (nomisCases.isNotEmpty() && dpsCases.size == nomisCases.size) {
      courtSentencingMappingApi.stubPostCourtCaseMappingsGivenNomisIds(
        nomisCases.map { it.id },
        dpsCases.map { it.courtCaseUuid },
      )
      nomisCases.zip(dpsCases).forEach { (nomisCase, dpsCase) ->
        nomisApi.stubGetCourtCaseForReconciliation(nomisCase.id, nomisCase)
        dpsApi.stubGetCourtCaseForReconciliation(dpsCase.courtCaseUuid, dpsCase)
      }
    }
  }
}
