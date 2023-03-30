package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ReferenceCode

@Service
class IncentivesReferenceService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val incentivesUpdateQueueService: IncentivesUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {

  private fun IncentiveLevel.toNomisIncentiveLevel(): ReferenceCode = ReferenceCode(
    code = code,
    domain = "IEP_LEVEL",
    description = description,
    active = active,
  )

  suspend fun globalIncentiveLevelChange(event: GlobalIncentiveChangedEvent) {
    val incentiveLevelCode = event.additionalInformation.incentiveLevel
    nomisApiService.getGlobalIncentiveLevel(incentiveLevelCode)?.also {
      // update
      incentivesApiService.getGlobalIncentiveLevel(incentiveLevelCode)
        .also { incentiveLevel -> nomisApiService.updateGlobalIncentiveLevel(incentiveLevel.toNomisIncentiveLevel()) }
    } ?: also {
      // insert
      incentivesApiService.getGlobalIncentiveLevel(incentiveLevelCode)
        .also { incentiveLevel -> nomisApiService.createGlobalIncentiveLevel(incentiveLevel.toNomisIncentiveLevel()) }
    }
  }

  data class AdditionalInformation(
    val incentiveLevel: String,
  )

  data class GlobalIncentiveChangedEvent(
    val additionalInformation: AdditionalInformation,
  )
}
