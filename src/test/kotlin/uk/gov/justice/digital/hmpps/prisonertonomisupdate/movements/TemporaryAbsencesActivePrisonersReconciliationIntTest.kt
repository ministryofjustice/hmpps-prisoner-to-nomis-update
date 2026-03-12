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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

class TemporaryAbsencesActivePrisonersReconciliationIntTest(
  @Autowired private val reconciliationService: TemporaryAbsencesActivePrisonersReconciliationService,
  @Autowired private val nomisMovementsApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = ExternalMovementsDpsApiExtension.dpsExternalMovementsServer
  private val nomisApi = NomisApiExtension.nomisApi

  @DisplayName("Temporary absences active prisoners reconciliation report")
  @Nested
  inner class GenerateTemporaryAbsencesActivePrisonersReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stuGetAllLatestBookings(
        activeOnly = true,
        response = BookingIdsWithLast(
          lastBookingId = 12345,
          prisonerIds = listOf(PrisonerIds(offenderNo = "A0001TZ", bookingId = 12345)),
        ),
      )

      nomisMovementsApi.stubGetTemporaryAbsences(offenderNo = "A0001TZ")
      dpsApi.stubGetTapReconciliationDetail(personIdentifier = "A0001TZ")
      mappingApi.stubGetTemporaryAbsenceMappingIds(prisonerNumber = "A0001TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateTapActivePrisonersReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-active-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-active-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("temporary-absences-active-reconciliation-report"), any(), isNull()) }
    }
  }
}
