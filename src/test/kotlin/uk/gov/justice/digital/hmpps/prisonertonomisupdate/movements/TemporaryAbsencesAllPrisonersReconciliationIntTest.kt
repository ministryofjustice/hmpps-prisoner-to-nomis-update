package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.TapMismatchTypes.AUTHORISATIONS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.MovementInOutCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonAuthorisationCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonMovementsCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonOccurrenceCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapCounts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

class TemporaryAbsencesAllPrisonersReconciliationIntTest(
  @Autowired private val reconciliationService: TemporaryAbsencesAllPrisonersReconciliationService,
  @Autowired private val nomisApi: ExternalMovementsNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = ExternalMovementsDpsApiExtension.dpsExternalMovementsServer

  @DisplayName("Temporary absences all prisoners reconciliation report")
  @Nested
  inner class GenerateTemporaryAbsencesAllPrisonersReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      NomisApiExtension.nomisApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = (1L..3).map { generateOffenderNo(sequence = it) },
      )

      // both same
      nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0001TZ")
      dpsApi.stubGetTapReconciliation(personIdentifier = "A0001TZ")

      // one count different
      nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0002TZ")
      dpsApi.stubGetTapReconciliation(
        personIdentifier = "A0002TZ",
        response = PersonTapCounts(
          authorisations = PersonAuthorisationCount(2),
          occurrences = PersonOccurrenceCount(2),
          movements = PersonMovementsCount(MovementInOutCount(3, 4), MovementInOutCount(5, 6)),
        ),
      )

      // all counts different
      nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0003TZ")
      dpsApi.stubGetTapReconciliation(
        personIdentifier = "A0003TZ",
        response = PersonTapCounts(
          authorisations = PersonAuthorisationCount(11),
          occurrences = PersonOccurrenceCount(12),
          movements = PersonMovementsCount(MovementInOutCount(13, 14), MovementInOutCount(15, 16)),
        ),
      )
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "3")
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for a single difference`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0002TZ",
            "type" to "AUTHORISATIONS",
            "dpsCount" to "2",
            "nomisCount" to "1",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output multiple mismatches for multiple differences`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "type" to "AUTHORISATIONS",
            "dpsCount" to "11",
            "nomisCount" to "1",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "type" to "OCCURRENCES",
            "dpsCount" to "12",
            "nomisCount" to "2",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "type" to "SCHEDULED_OUT",
            "dpsCount" to "13",
            "nomisCount" to "3",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "type" to "SCHEDULED_IN",
            "dpsCount" to "14",
            "nomisCount" to "4",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "type" to "UNSCHEDULED_OUT",
            "dpsCount" to "15",
            "nomisCount" to "5",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0003TZ",
            "type" to "UNSCHEDULED_IN",
            "dpsCount" to "16",
            "nomisCount" to "6",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("temporary-absences-all-reconciliation-report"), any(), isNull()) }
    }
  }

  @Nested
  inner class SinglePrisonerAllPrisonerReconciliationReport {

    @Nested
    inner class RunReport {
      @Test
      fun `no differences found`() {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0001TZ")
        dpsApi.stubGetTapReconciliation(personIdentifier = "A0001TZ")

        webTestClient.getAllTapsPrisonerReconOk()
          .apply {
            assertThat(this).isEmpty()
          }
      }

      @Test
      fun `one different count`() {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0001TZ")
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = PersonTapCounts(
            authorisations = PersonAuthorisationCount(2),
            occurrences = PersonOccurrenceCount(2),
            movements = PersonMovementsCount(MovementInOutCount(3, 4), MovementInOutCount(5, 6)),
          ),
        )

        webTestClient.getAllTapsPrisonerReconOk()
          .apply {
            with(this[0]) {
              assertThat(offenderNo).isEqualTo("A0001TZ")
              assertThat(type).isEqualTo(AUTHORISATIONS)
              assertThat(dpsCount).isEqualTo(2)
              assertThat(nomisCount).isEqualTo(1)
            }
          }
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return error for unknown offender`() {
        webTestClient.getAllTapsPrisonerRecon("UNKNOWN")
          .expectStatus().is5xxServerError
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/external-movements/all-taps/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/external-movements/all-taps/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/external-movements/all-taps/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    private fun WebTestClient.getAllTapsPrisonerReconOk(offenderNo: String = "A0001TZ") = getAllTapsPrisonerRecon(offenderNo)
      .expectStatus().isOk
      .expectBody<List<MismatchPrisonerTaps>>()
      .returnResult().responseBody!!

    private fun WebTestClient.getAllTapsPrisonerRecon(offenderNo: String = "A0001TZ") = get().uri("/external-movements/all-taps/$offenderNo/reconciliation")
      .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
      .exchange()
  }
}
