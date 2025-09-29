package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

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
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationId

class OrganisationsReconciliationIntTest(
  @Autowired private val organisationsReconciliationService: OrganisationsReconciliationService,
  @Autowired private val nomisApi: OrganisationsNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer

  @DisplayName("Organisations reconciliation report")
  @Nested
  inner class GenerateOrganisationsReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetCorporateOrganisationIds(
        content = listOf(
          CorporateOrganisationIdResponse(1),
          CorporateOrganisationIdResponse(2),
          CorporateOrganisationIdResponse(3),
        ),
      )
      dpsApi.stubGetOrganisationIds(
        content = listOf(
          SyncOrganisationId(1),
          SyncOrganisationId(2),
        ),
      )
      nomisApi.stubGetCorporateOrganisation(1, corporateOrganisation().copy(id = 1, name = "Boots"))
      dpsApi.stubGetOrganisation(1, organisationDetails().copy(organisationId = 1, organisationName = "Boots"))

      nomisApi.stubGetCorporateOrganisation(2, corporateOrganisation().copy(id = 2, name = "Police"))
      dpsApi.stubGetOrganisation(2, organisationDetails().copy(organisationId = 1, organisationName = "Army"))

      nomisApi.stubGetCorporateOrganisation(3, corporateOrganisation().copy(id = 3, name = "National Probation Service"))
      dpsApi.stubGetOrganisation(3, HttpStatus.NOT_FOUND)
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      organisationsReconciliationService.generateOrganisationsReconciliationReport()

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-requested"),
        check { assertThat(it).containsEntry("organisations", "3") },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() = runTest {
      organisationsReconciliationService.generateOrganisationsReconciliationReport()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("organisationIds", "[2, 3]")
        },
        isNull(),
      )
    }

    @Test
    fun `will finish report even if one request keeps failing`() = runTest {
      dpsApi.stubGetOrganisation(3, HttpStatus.SERVICE_UNAVAILABLE)

      organisationsReconciliationService.generateOrganisationsReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("organisationIds", "[2]")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("organisationId", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a missing DPS record`() = runTest {
      organisationsReconciliationService.generateOrganisationsReconciliationReport()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-mismatch"),
        eq(
          mapOf(
            "dpsCount" to "2",
            "nomisCount" to "3",
          ),
        ),
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("organisations-reports-reconciliation-report"), any(), isNull()) }
  }
}
