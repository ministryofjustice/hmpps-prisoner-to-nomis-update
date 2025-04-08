package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertSource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.PersonReference

@Service
class VisitBalanceService(
  private val telemetryClient: TelemetryClient,
) {
  suspend fun createVisitBalanceAdjustment(visitBalanceAdjustmentEvent: VisitBalanceAdjustmentEvent) {
    val telemetryMap = mapOf(
      "prisonNumber" to visitBalanceAdjustmentEvent.personReference.findNomsNumber(),
    )
    telemetryClient.trackEvent("Received prison-visit-allocation.updated event", telemetryMap, null)
  }
}

enum class VisitBalanceAdjustmentSource {
  DPS,
  NOMIS,
}

data class VisitBalanceAdjustmentEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: VisitBalanceAdjustmentAdditionalInformation,
)
data class VisitBalanceAdjustmentAdditionalInformation(
  val source: AlertSource,
)
