package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ReferenceCode

@Service
class IncentivesReferenceService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
) {

  private fun IncentiveLevel.toNomisIncentiveLevel(): ReferenceCode = ReferenceCode(
    code = code,
    domain = "IEP_LEVEL",
    description = name,
    active = active,
  )

  suspend fun globalIncentiveLevelChange(event: GlobalIncentiveChangedEvent) {
    val incentiveLevelCode = event.additionalInformation.incentiveLevel
    nomisApiService.getGlobalIncentiveLevel(incentiveLevelCode)?.also {
      incentivesApiService.getGlobalIncentiveLevel(incentiveLevelCode)
        .also { incentiveLevel ->
          nomisApiService.updateGlobalIncentiveLevel(incentiveLevel.toNomisIncentiveLevel())
          trackEvent("global-incentive-level-updated", incentiveLevel)
        }
    } ?: also {
      incentivesApiService.getGlobalIncentiveLevel(incentiveLevelCode)
        .also { incentiveLevel ->
          nomisApiService.createGlobalIncentiveLevel(incentiveLevel.toNomisIncentiveLevel())
          trackEvent("global-incentive-level-inserted", incentiveLevel)
        }
    }
  }

  private fun trackEvent(eventName: String, incentiveLevel: IncentiveLevel) {
    telemetryClient.trackEvent(
      eventName,
      mutableMapOf(
        "code" to incentiveLevel.code,
        "description" to incentiveLevel.name,
        "active" to incentiveLevel.active.toString(),
      ),
      null,
    )
  }

  suspend fun globalIncentiveLevelsReorder() {
    incentivesApiService.getGlobalIncentiveLevelsInOrder().also {
      val orderedIepCodes = it.toCodeList()
      nomisApiService.globalIncentiveLevelReorder(orderedIepCodes)
      telemetryClient.trackEvent(
        "global-incentive-levels-reordered",
        mutableMapOf("orderedIepCodes" to orderedIepCodes.toString()),
        null,
      )
    }
  }

  data class AdditionalInformation(
    val incentiveLevel: String,
  )

  data class GlobalIncentiveChangedEvent(
    val additionalInformation: AdditionalInformation,
  )
}
