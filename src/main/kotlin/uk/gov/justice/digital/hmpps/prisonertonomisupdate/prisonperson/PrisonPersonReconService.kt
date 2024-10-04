package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonPersonReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesSyncDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes.PhysicalAttributesDpsApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class PrisonPersonReconService(
  private val dpsApi: PhysicalAttributesDpsApiService,
  private val prisonPersonNomisApi: PrisonPersonNomisApiService,
  private val nomisApi: NomisApiService,
  @Value("\${reports.prisonperson.reconciliation.page-size:20}") private val pageSize: Long = 20,
  // TODO SDIT-2016 Remove this feature switch when live in all environments
  @Value("\${feature.recon.prison-person.profile-details}") private val profileDetailsReconciliation: Boolean = true,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<String> =
    activePrisonersCount
      .asPages(pageSize)
      .flatMap { page ->
        val activePrisoners = getActivePrisonersForPage(page)

        withContext(Dispatchers.Unconfined) {
          activePrisoners.map { async { checkPrisoner(it.offenderNo) } }
        }.awaitAll().filterNotNull()
      }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) =
    runCatching { nomisApi.getActivePrisoners(page.first, page.second).content }
      .onFailure {
        telemetryClient.trackEvent("prison-person-reconciliation-page-error", mapOf("page" to page.first.toString(), "error" to (it.message ?: "unknown error")))
      }
      .getOrElse { emptyList() }

  suspend fun checkPrisoner(offenderNo: String): String? = runCatching {
    val (nomisPrisoner, dpsPrisoner) = withContext(Dispatchers.Unconfined) {
      async { doApiCallWithRetries { prisonPersonNomisApi.getReconciliation(offenderNo) } } to
        async { doApiCallWithRetries { dpsApi.getPhysicalAttributes(offenderNo) } }
    }.awaitBoth()

    val differenceTelemetry = findDifferenceTelemetry(offenderNo, nomisPrisoner, dpsPrisoner)
    return if (differenceTelemetry.isNotEmpty()) {
      offenderNo.also {
        telemetryClient.trackEvent("prison-person-reconciliation-prisoner-failed", differenceTelemetry)
      }
    } else {
      null
    }
  }.onFailure {
    telemetryClient.trackEvent("prison-person-reconciliation-prisoner-error", mapOf("offenderNo" to offenderNo, "error" to (it.message ?: "unknown error")))
  }.getOrNull()

  private fun findDifferenceTelemetry(offenderNo: String, nomisPrisoner: PrisonPersonReconciliationResponse?, dpsPrisoner: PhysicalAttributesSyncDto?): MutableMap<String, String> {
    val failureTelemetry = mutableMapOf<String, String>()
    val differences = physicalAttributesDifferences(nomisPrisoner, dpsPrisoner) +
      physicalAttributesProfileDetailsDifferences(nomisPrisoner, dpsPrisoner)

    if (differences.isNotEmpty()) {
      failureTelemetry["offenderNo"] = offenderNo
      failureTelemetry["differences"] = differences.joinToString()
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

  private fun physicalAttributesDifferences(nomisPrisoner: PrisonPersonReconciliationResponse?, dpsPrisoner: PhysicalAttributesSyncDto?): List<String> {
    val differences = mutableListOf<String>()

    if (nomisPrisoner?.height != dpsPrisoner?.height) {
      differences += "height"
    }

    if (nomisPrisoner?.weight != dpsPrisoner?.weight) {
      differences += "weight"
    }

    return differences.toList()
  }

  private fun physicalAttributesProfileDetailsDifferences(nomisPrisoner: PrisonPersonReconciliationResponse?, dpsPrisoner: PhysicalAttributesSyncDto?): List<String> {
    if (!profileDetailsReconciliation) {
      return emptyList()
    }

    val differences = mutableListOf<String>()

    if (nomisPrisoner?.face != dpsPrisoner?.face) {
      differences += "face"
    }

    if (nomisPrisoner?.build != dpsPrisoner?.build) {
      differences += "build"
    }

    if (nomisPrisoner?.facialHair != dpsPrisoner?.facialHair) {
      differences += "facialHair"
    }

    if (nomisPrisoner?.hair != dpsPrisoner?.hair) {
      differences += "hair"
    }

    if (nomisPrisoner?.leftEyeColour != dpsPrisoner?.leftEyeColour) {
      differences += "leftEyeColour"
    }

    if (nomisPrisoner?.rightEyeColour != dpsPrisoner?.rightEyeColour) {
      differences += "rightEyeColour"
    }

    if (nomisPrisoner?.shoeSize != dpsPrisoner?.shoeSize) {
      differences += "shoeSize"
    }

    return differences.toList()
  }
}
