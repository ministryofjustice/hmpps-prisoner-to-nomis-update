package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

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

private const val DPS_CASE_2_ID = "21111111-1111-1111-1111-111111111111"

private const val NOMIS_CASE_ID = 1L
private const val NOMIS_CASE_2_ID = 2L

class CourtSentencingReconciliationResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApi: CourtSentencingMappingApiMockServer

  private val dpsApi = CourtSentencingApiExtension.courtSentencingApi
  private val nomisPrisonerApi = NomisApiExtension.Companion.nomisApi

  @DisplayName("PUT /court-sentencing/court-cases/prisoner/reports/reconciliation")
  @Nested
  inner class GenerateCourtCasePrisonerReconciliationReport {
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
        "$OFFENDER_NO",
        listOf(nomisCaseResponse().copy(id = NOMIS_CASE_ID)),
        listOf(dpsCourtCaseResponse().copy(courtCaseUuid = DPS_CASE_ID)),
      )
      stubCases("A0002TZ", emptyList(), emptyList())
      stubCases(
        "A0003TZ",
        listOf(nomisCaseResponse().copy(id = 2L, caseStatus = CodeDescription("I", "Inactive"))),
        listOf(dpsCourtCaseResponse().copy(courtCaseUuid = "11111111-1111-1111-1111-111111111112", active = true)),
      )
      stubCases("A0004TZ", emptyList(), emptyList())
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/court-sentencing/court-cases/prisoner/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() {
      webTestClient.put().uri("/court-sentencing/court-cases/prisoner/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "4")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a difference in the  DPS record`() {
      webTestClient.put().uri("/court-sentencing/court-cases/prisoner/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-case-prisoner-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "dpsCaseId" to "11111111-1111-1111-1111-111111111112",
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
            .headers(setAuthorisation(roles = listOf("NOMIS_SENTENCING")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // for the purposes of DPS - status of case is either active or inactive. Other values exist in nomis
            .jsonPath("$[0].mismatch.differences.size()").isEqualTo(NOMIS_CASE_ID)
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

  private fun stubCases(offenderNo: String, nomisCases: List<CourtCaseResponse>, dpsCases: List<ReconciliationCourtCase>) {
    nomisApi.stubGetCourtCaseIdsByOffenderNo(offenderNo, response = nomisCases.map { it.id })
    if (nomisCases.isNotEmpty()) {
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
