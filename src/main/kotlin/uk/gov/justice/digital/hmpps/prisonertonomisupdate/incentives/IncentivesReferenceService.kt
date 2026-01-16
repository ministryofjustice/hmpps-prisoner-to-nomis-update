package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateGlobalIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateGlobalIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PrisonIncentiveLevelRequest

@Service
class IncentivesReferenceService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
) {

  private fun IncentiveLevel.toCreateGlobalIncentiveRequest(): CreateGlobalIncentiveRequest = CreateGlobalIncentiveRequest(
    code = code,
    description = name,
    active = active,
  )
  private fun IncentiveLevel.toUpdateGlobalIncentiveRequest(): UpdateGlobalIncentiveRequest = UpdateGlobalIncentiveRequest(
    description = name,
    active = active,
  )

  private fun PrisonIncentiveLevel.toNomisPrisonIncentiveLevel(): PrisonIncentiveLevelRequest = PrisonIncentiveLevelRequest(
    levelCode = levelCode,
    active = active,
    defaultOnAdmission = defaultOnAdmission,
    visitOrderAllowance = visitOrders.toInt(),
    privilegedVisitOrderAllowance = privilegedVisitOrders.toInt(),
    remandTransferLimitInPence = remandTransferLimitInPence.toInt(),
    remandSpendLimitInPence = remandSpendLimitInPence.toInt(),
    convictedTransferLimitInPence = convictedTransferLimitInPence.toInt(),
    convictedSpendLimitInPence = convictedSpendLimitInPence.toInt(),
  )

  suspend fun globalIncentiveLevelChange(event: GlobalIncentiveChangedEvent) {
    val incentiveLevelCode = event.additionalInformation.incentiveLevel
    nomisApiService.getGlobalIncentiveLevel(incentiveLevelCode)?.also {
      incentivesApiService.getGlobalIncentiveLevel(incentiveLevelCode)
        .also { incentiveLevel ->
          nomisApiService.updateGlobalIncentiveLevel(incentiveLevelCode, incentiveLevel.toUpdateGlobalIncentiveRequest())
          trackEvent("global-incentive-level-updated", incentiveLevel)
        }
    } ?: also {
      incentivesApiService.getGlobalIncentiveLevel(incentiveLevelCode)
        .also { incentiveLevel ->
          nomisApiService.createGlobalIncentiveLevel(incentiveLevel.toCreateGlobalIncentiveRequest())
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

  private fun trackEvent(eventName: String, incentiveLevel: PrisonIncentiveLevel) {
    telemetryClient.trackEvent(
      eventName,
      mutableMapOf(
        "code" to incentiveLevel.levelCode,
        "prison" to incentiveLevel.prisonId,
        "defaultOnAdmission" to incentiveLevel.defaultOnAdmission.toString(),
        "active" to incentiveLevel.active.toString(),
        "visitOrders" to incentiveLevel.visitOrders,
        "privilegedVisitOrders" to incentiveLevel.privilegedVisitOrders,
        "remandTransferLimitInPence" to incentiveLevel.remandTransferLimitInPence,
        "remandSpendLimitInPence" to incentiveLevel.remandSpendLimitInPence,
        "convictedSpendLimitInPence" to incentiveLevel.convictedSpendLimitInPence,
        "convictedTransferLimitInPence" to incentiveLevel.convictedTransferLimitInPence,
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

  suspend fun prisonIncentiveLevelChange(event: PrisonIncentiveChangedEvent) {
    val incentiveLevelCode = event.additionalInformation.incentiveLevel
    val prisonId = event.additionalInformation.prisonId
    nomisApiService.getPrisonIncentiveLevel(prisonId, incentiveLevelCode)?.also {
      incentivesApiService.getPrisonIncentiveLevel(prisonId, incentiveLevelCode)
        .also { prisonIncentiveLevelData ->
          nomisApiService.updatePrisonIncentiveLevel(prisonId, prisonIncentiveLevelData.toNomisPrisonIncentiveLevel())
          trackEvent("prison-incentive-level-updated", prisonIncentiveLevelData)
        }
    } ?: also {
      incentivesApiService.getPrisonIncentiveLevel(prisonId, incentiveLevelCode)
        .also { prisonIncentiveLevelData ->
          nomisApiService.createPrisonIncentiveLevel(prisonId, prisonIncentiveLevelData.toNomisPrisonIncentiveLevel())
          trackEvent("prison-incentive-level-inserted", prisonIncentiveLevelData)
        }
    }
  }

  data class AdditionalInformation(
    val incentiveLevel: String,
  )

  data class AdditionalInformationPrisonLevel(
    val incentiveLevel: String,
    val prisonId: String,
  )

  data class GlobalIncentiveChangedEvent(
    val additionalInformation: AdditionalInformation,
  )

  data class PrisonIncentiveChangedEvent(
    val additionalInformation: AdditionalInformationPrisonLevel,
  )
}
