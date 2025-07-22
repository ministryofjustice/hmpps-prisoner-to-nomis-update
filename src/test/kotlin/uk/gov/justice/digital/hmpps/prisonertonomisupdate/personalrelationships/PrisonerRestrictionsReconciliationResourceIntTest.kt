package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RestrictionIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.nomisPrisonerRestriction

class PrisonerRestrictionsReconciliationResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var mappingApi: ContactPersonMappingApiMockServer

  @Autowired
  private lateinit var nomisApi: ContactPersonNomisApiMockServer

  private val dpsApi = ContactPersonDpsApiExtension.Companion.dpsContactPersonServer

  @DisplayName("PUT /contact-person/prisoner-restriction/reports/reconciliation")
  @Nested
  inner class GeneratePrisonerRestrictionsReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetPrisonerRestrictionIds(
        lastRestrictionId = 0,
        response = RestrictionIdsWithLast(
          lastRestrictionId = 3,
          restrictionIds = (1L..3).toList(),
        ),
      )
      nomisApi.stubGetPrisonerRestrictionById(1, nomisPrisonerRestriction())
      mappingApi.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = 1, dpsRestrictionId = "101")
      dpsApi.stubGetPrisonerRestriction(prisonerRestrictionId = 101, prisonerRestriction())

      nomisApi.stubGetPrisonerRestrictionById(2, nomisPrisonerRestriction())
      mappingApi.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = 2, mapping = null)

      nomisApi.stubGetPrisonerRestrictionById(3, nomisPrisonerRestriction())
      mappingApi.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = 3, dpsRestrictionId = "103")
      dpsApi.stubGetPrisonerRestriction(prisonerRestrictionId = 103, prisonerRestriction())
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.post().uri("/contact-person/prisoner-restriction/reports/reconciliation")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_UPDATE__RECONCILIATION__R")))
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-restriction-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() {
      webTestClient.post().uri("/contact-person/prisoner-restriction/reports/reconciliation")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_UPDATE__RECONCILIATION__R")))
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-restriction-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("restrictions-count", "3")
          assertThat(it).containsEntry("restrictionIds", "2")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a missing mapping record`() {
      webTestClient.post().uri("/contact-person/prisoner-restriction/reports/reconciliation")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_UPDATE__RECONCILIATION__R")))
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-restriction-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisRestrictionId" to "2",
            "reason" to "restriction-mapping-missing",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("contact-person-prisoner-restriction-reconciliation-report"), any(), isNull()) }
    }
  }
}
