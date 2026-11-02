package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

class PersonLocationReconciliationIntTest(
  @Autowired private val reconciliationService: PersonLocationReconciliationService,
  @Autowired private val nomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = dpsPersonLocationServer
  private val prisonerApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report")
  @Nested
  inner class GenerateReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      prisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      nomisApi.stubGetOffenderPersonLocations(offenderNo = "A0001TZ")
      dpsApi.stubReconciliation(personIdentifier = "A0001TZ")
      mappingApi.stubGetPersonLocationMappings()
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generatePersonLocationReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    @Test
    fun `will report failure to reconcile prisoner`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(offenderNo = "A0001TZ", status = INTERNAL_SERVER_ERROR)

      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("offenderNo", "A0001TZ")
          assertThat(it).containsEntry("reason", "500 Internal Server Error from GET http://localhost:8082/movements/A0001TZ/person-locations")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("person-location-reconciliation-report"), any(), isNull()) }
  }
}
