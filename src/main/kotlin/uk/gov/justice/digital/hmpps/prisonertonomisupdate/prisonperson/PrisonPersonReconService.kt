package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonPersonReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class PrisonPersonReconService(
  private val dpsApi: PrisonPersonDpsApiService,
  private val nomisApi: PrisonPersonNomisApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun checkPrisoner(offenderNo: String) = runCatching {
    val (nomisPrisoner, dpsPrisoner) = withContext(Dispatchers.Unconfined) {
      async { nomisApi.getReconciliation(offenderNo) } to
        async { dpsApi.getPhysicalAttributes(offenderNo) }
    }.awaitBoth()

    findDifferences(offenderNo, nomisPrisoner, dpsPrisoner)
      .takeIf { it.isNotEmpty() }
      ?.also {
        telemetryClient.trackEvent("prison-person-reconciliation-report-failed", it)
      }
  }.onFailure {
    telemetryClient.trackEvent("prison-person-reconciliation-report-error", mapOf("offenderNo" to offenderNo, "error" to (it.message ?: "unknown error")))
  }

  private fun findDifferences(offenderNo: String, nomisPrisoner: PrisonPersonReconciliationResponse?, dpsPrisoner: PhysicalAttributesDto?): MutableMap<String, String> {
    val failureTelemetry = mutableMapOf<String, String>()

    if (nomisPrisoner?.height != dpsPrisoner?.height?.value) {
      failureTelemetry["heightNomis"] = nomisPrisoner?.height.toString()
      failureTelemetry["heightDps"] = dpsPrisoner?.height?.value.toString()
    }

    if (nomisPrisoner?.weight != dpsPrisoner?.weight?.value) {
      failureTelemetry["weightNomis"] = nomisPrisoner?.weight.toString()
      failureTelemetry["weightDps"] = dpsPrisoner?.weight?.value.toString()
    }

    if (failureTelemetry.isNotEmpty()) {
      failureTelemetry["offenderNo"] = offenderNo
    }

    // If we found any differences then advise if the prisoner is missing from either system
    if (failureTelemetry.isNotEmpty()) {
      if (nomisPrisoner == null) {
        failureTelemetry["nomisPrisoner"] = "null"
      }
      if (dpsPrisoner == null) {
        failureTelemetry["dpsPrisoner"] = "null"
      }
    }

    return failureTelemetry
  }
}
