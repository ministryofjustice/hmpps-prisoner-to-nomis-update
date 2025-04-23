package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import java.util.UUID

@Service
class VisitBalanceService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: VisitBalanceDpsApiService,
  private val nomisApiService: VisitBalanceNomisApiService,
) {
  suspend fun visitBalanceAdjustmentCreated(event: VisitBalanceAdjustmentEvent) {
    val visitBalanceAdjustmentId = event.additionalInformation.visitBalanceAdjustmentUuid.toString()
    val telemetry = telemetryOf("visitBalanceAdjustmentId" to visitBalanceAdjustmentId)

    dpsApiService.getVisitBalanceAdjustment(visitBalanceAdjustmentId).also {
      nomisApiService.createVisitBalanceAdjustment(it.prisonerId, it.toNomisCreateVisitBalanceAdjustmentRequest())
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-success",
        telemetry + ("prisonNumber" to it.prisonerId) + ("visitBalanceChange" to it.changeToVoBalance.toString()) +
          ("privilegeVisitBalanceChange" to it.changeToPvoBalance.toString()),
      )
    }
  }

  suspend fun visitBalanceUpdated(event: VisitBalanceEvent) {
    val offenderNo: String = event.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetry = telemetryOf("prisonNumber" to offenderNo)

    val balance = dpsApiService.getVisitBalance(offenderNo)
    if (balance != null) {
      nomisApiService.updateVisitBalance(balance.prisonerId, balance.toNomisUpdateVisitBalanceRequest())
      telemetryClient.trackEvent(
        "visitbalance-balance-synchronisation-updated-success",
        telemetry + ("visitBalance" to balance.voBalance.toString()) + ("privilegedVisitBalance" to balance.pvoBalance.toString()),
      )
    }
  }
}

data class VisitBalanceEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
)

data class VisitBalanceAdjustmentEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: VisitBalanceAdjustmentAdditionalInformation,
)

data class VisitBalanceAdjustmentAdditionalInformation(
  val visitBalanceAdjustmentUuid: UUID,
)

fun VisitBalanceAdjustmentDto.toNomisCreateVisitBalanceAdjustmentRequest() = CreateVisitBalanceAdjustmentRequest(
  adjustmentReasonCode = adjustmentReasonCode.value,
  adjustmentDate = adjustmentDate,
  visitOrderChange = changeToVoBalance,
  previousVisitOrderCount = oldVoBalance,
  privilegedVisitOrderChange = changeToPvoBalance,
  previousPrivilegedVisitOrderCount = oldPvoBalance,
  comment = comment,
  expiryBalance = expiryBalance,
  expiryDate = expiryDate,
  endorsedStaffId = endorsedStaffId,
  authorisedStaffId = authorisedStaffId,
)

fun PrisonerBalanceDto.toNomisUpdateVisitBalanceRequest() = UpdateVisitBalanceRequest(
  remainingVisitOrders = voBalance,
  remainingPrivilegedVisitOrders = pvoBalance,
)
