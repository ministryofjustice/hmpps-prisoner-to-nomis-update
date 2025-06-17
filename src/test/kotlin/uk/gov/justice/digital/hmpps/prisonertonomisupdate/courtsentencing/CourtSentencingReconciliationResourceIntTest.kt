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
          prisonerIds = (1L..4).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      stubCases("A0001TZ", listOf(nomisCaseResponse().copy(id = 1L)), listOf(dpsCourtCaseResponse().copy(courtCaseUuid = "11111111-1111-1111-1111-111111111111")))
      stubCases("A0002TZ", emptyList(), emptyList())
      stubCases("A0003TZ", listOf(nomisCaseResponse().copy(id = 2L, caseStatus = CodeDescription("I", "Inactive"))), listOf(dpsCourtCaseResponse().copy(courtCaseUuid = "11111111-1111-1111-1111-111111111112", active = true)))
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
      await untilAsserted { verify(telemetryClient).trackEvent(eq("court-case-prisoner-reconciliation-report"), any(), isNull()) }
    }
  }

  private fun stubCases(offenderNo: String, nomisCases: List<CourtCaseResponse>, dpsCases: List<ReconciliationCourtCase>) {
    nomisApi.stubGetCourtCaseIdsByOffenderNo(offenderNo, response = nomisCases.map { it.id })

    nomisCases.zip(dpsCases).forEach { (nomisCase, dpsCase) ->
      courtSentencingMappingApi.stubGetCourtCaseMappingGivenNomisId(nomisCase.id, dpsCase.courtCaseUuid)
      nomisApi.stubGetCourtCaseForReconciliation(nomisCase.id, nomisCase)
      dpsApi.stubGetCourtCaseForReconciliation(dpsCase.courtCaseUuid, dpsCase)
    }
  }
}
