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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RestrictionIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.nomisPrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerRestriction
import java.time.LocalDate

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
          lastRestrictionId = 13,
          restrictionIds = (1L..4).toList(),
        ),
      )

      stubRestrictions(nomisId = 1, dpsId = 101)

      stubRestrictions(nomisId = 2, dpsId = null)

      stubRestrictions(nomisId = 3, dpsId = 103, dpsRestriction = null)

      stubRestrictions(
        nomisId = 4,
        dpsId = 104,
        nomisRestriction(4).copy(effectiveDate = LocalDate.now().minusDays(1)),
        dpsRestriction = dpsRestriction().copy(effectiveDate = LocalDate.now().minusDays(2)),
      )
    }

    private fun stubRestrictions(nomisId: Long, dpsId: Long? = nomisId + 100, nomisRestriction: PrisonerRestriction = nomisRestriction(nomisId), dpsRestriction: SyncPrisonerRestriction? = dpsRestriction()) {
      nomisApi.stubGetPrisonerRestrictionById(nomisId, nomisRestriction)
      if (dpsId != null) {
        mappingApi.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = nomisId, dpsRestrictionId = dpsId.toString())
        if (dpsRestriction != null) {
          dpsApi.stubGetPrisonerRestrictionOrNull(prisonerRestrictionId = dpsId, dpsRestriction.copy(prisonerRestrictionId = dpsId))
        } else {
          dpsApi.stubGetPrisonerRestrictionOrNull(prisonerRestrictionId = dpsId, null)
        }
      } else {
        mappingApi.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = nomisId, mapping = null)
      }
    }

    private fun nomisRestriction(id: Long) = nomisPrisonerRestriction().copy(
      id = id,
      bookingSequence = 1,
      offenderNo = "A1234KT",
      type = CodeDescription("BAN", "Banned"),
      effectiveDate = LocalDate.now(),
      expiryDate = null,
      comment = "Banned for life",
    )

    private fun dpsRestriction() = prisonerRestriction().copy(
      prisonerRestrictionId = 1,
      prisonerNumber = "A1234KT",
      restrictionType = "BAN",
      effectiveDate = LocalDate.now(),
      currentTerm = true,
      expiryDate = null,
      commentText = "Banned for life",
    )

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
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("restrictions-count", "4")
          assertThat(it).containsEntry("restrictionIds", "2, 3, 4")
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

    @Test
    fun `will output a mismatch when there is a missing DPS record`() {
      webTestClient.post().uri("/contact-person/prisoner-restriction/reports/reconciliation")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_UPDATE__RECONCILIATION__R")))
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-restriction-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisRestrictionId" to "3",
            "reason" to "dps-record-missing",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a difference`() {
      webTestClient.post().uri("/contact-person/prisoner-restriction/reports/reconciliation")
        .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_UPDATE__RECONCILIATION__R")))
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-restriction-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisRestrictionId" to "4",
            "dpsRestrictionId" to "104",
            "offenderNo" to "A1234KT",
            "reason" to "restriction-different-details",
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
