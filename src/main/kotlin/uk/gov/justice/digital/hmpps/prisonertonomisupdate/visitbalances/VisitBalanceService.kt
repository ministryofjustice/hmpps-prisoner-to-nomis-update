package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import java.util.UUID

@Service
class VisitBalanceService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: VisitBalanceDpsApiService,
  private val nomisApiService: VisitBalanceNomisApiService,
) {
  suspend fun createVisitBalanceAdjustment(event: VisitBalanceAdjustmentEvent) {
    val visitBalanceAdjustmentId = event.additionalInformation.visitBalanceAdjustmentUuid.toString()
    val telemetry = telemetryOf("visitBalanceAdjustmentId" to visitBalanceAdjustmentId)

    if (event.originatedInDps()) {
      dpsApiService.getVisitBalanceAdjustment(visitBalanceAdjustmentId).also {
        nomisApiService.createVisitBalanceAdjustment(it.prisonerId, it.toNomisCreateVisitBalanceAdjustmentRequest())
        telemetryClient.trackEvent(
          "visitbalance-adjustment-synchronisation-created-success",
          telemetry + ("prisonNumber" to it.prisonerId) + ("visitBalanceChange" to it.changeToVoBalance.toString()) +
            ("privilegeVisitBalanceChange" to it.changeToPvoBalance.toString()),
        )
      }
    } else {
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-skipped",
        telemetry,
      )
    }
  }
}

data class VisitBalanceAdjustmentEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: VisitBalanceAdjustmentAdditionalInformation,
)

data class VisitBalanceAdjustmentAdditionalInformation(
  val source: VisitBalanceAdjustmentSource,
  val visitBalanceAdjustmentUuid: UUID,
)

enum class VisitBalanceAdjustmentSource { DPS, NOMIS }

fun VisitBalanceAdjustmentEvent.originatedInDps() = this.additionalInformation.source == VisitBalanceAdjustmentSource.DPS

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
