package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonDpsApiExtension.Companion.prisonPersonDpsApi

class PrisonPersonReconIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var prisonPersonNomisApi: PrisonPersonNomisApiMockServer

  @Autowired
  private lateinit var reconciliationService: PrisonPersonReconService

  @Nested
  inner class SinglePrisoner {
    @Test
    fun `should do nothing if no differences`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 180, weight = 80)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report differences to height and weight`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 170, weight = 70)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "heightDps" to "170",
              "heightNomis" to "180",
              "weightDps" to "70",
              "weightNomis" to "80",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if null in DPS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = null, weight = null)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "heightDps" to "null",
              "weightDps" to "null",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if null in NOMIS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 170, weight = 70)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = null, weight = null)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "heightNomis" to "null",
              "weightNomis" to "null",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if missing from DPS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(NOT_FOUND)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "dpsPrisoner" to "null",
              "heightDps" to "null",
              "heightNomis" to "180",
              "weightDps" to "null",
              "weightNomis" to "80",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if missing from NOMIS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 170, weight = 70)
      prisonPersonNomisApi.stubGetReconciliation(NOT_FOUND)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "nomisPrisoner" to "null",
              "heightNomis" to "null",
              "heightDps" to "170",
              "weightNomis" to "null",
              "weightDps" to "70",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should not report differences if null in both`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = null, weight = null)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = null, weight = null)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should not report differences if missing from both`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(NOT_FOUND)
      prisonPersonNomisApi.stubGetReconciliation(NOT_FOUND)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report errors from DPS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(HttpStatus.INTERNAL_SERVER_ERROR)
      prisonPersonNomisApi.stubGetReconciliation("A1234AA")

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "error" to "500 Internal Server Error from GET http://localhost:8097/prisoners/A1234AA/physical-attributes",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report errors from NOMIS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
      prisonPersonNomisApi.stubGetReconciliation(BAD_GATEWAY)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "error" to "502 Bad Gateway from GET http://localhost:8082/prisoners/A1234AA/prison-person/reconciliation",
            ),
          )
        },
        isNull(),
      )
    }
  }
}
