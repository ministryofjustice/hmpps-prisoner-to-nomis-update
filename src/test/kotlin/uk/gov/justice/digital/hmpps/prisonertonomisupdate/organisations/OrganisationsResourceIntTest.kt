package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiMockServer.Companion.organisationDetails

class OrganisationsResourceIntTest : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @Autowired
  private lateinit var nomisApi: OrganisationsNomisApiMockServer

  private val dpsApi = OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer

  @DisplayName("PUT /organisations/reports/reconciliation")
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
      nomisApi.stubGetCorporateOrganisation(1, corporateOrganisation().copy(id = 1, name = "Boots"))
      dpsApi.stubGetOrganisation(1, organisationDetails().copy(organisationId = 1, organisationName = "Boots"))

      nomisApi.stubGetCorporateOrganisation(2, corporateOrganisation().copy(id = 2, name = "Police"))
      dpsApi.stubGetOrganisation(2, organisationDetails().copy(organisationId = 1, organisationName = "Army"))

      nomisApi.stubGetCorporateOrganisation(3, corporateOrganisation().copy(id = 3, name = "National Probation Service"))
      dpsApi.stubGetOrganisation(3, HttpStatus.NOT_FOUND)
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/organisations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-requested"),
        check { assertThat(it).containsEntry("organisations", "3") },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() {
      webTestClient.put().uri("/organisations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("organisations-reports-reconciliation-report"),
        check { assertThat(it).containsEntry("mismatch-count", "2") },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("organisations-reports-reconciliation-report"), any(), isNull()) }
  }
}
